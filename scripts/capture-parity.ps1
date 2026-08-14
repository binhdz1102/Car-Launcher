[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet("baseline", "candidate")]
    [string]$Label,
    [Parameter(Mandatory)]
    [string]$ApkPath,
    [string]$Serial = "emulator-5554",
    [int]$UserId = 10,
    [string]$ArtifactsRoot,
    [string]$InstrumentationApk,
    [switch]$Install,
    [switch]$RequireInstrumentation,
    [switch]$RequireFixtures,
    [ValidateSet("home", "app-grid", "recents", "calm-mode", "widget-host", "map-tos")]
    [string]$Scenario = "home",
    [Alias("LaunchHome")]
    [switch]$LaunchScenario
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ArtifactsRoot)) {
    $ArtifactsRoot = Join-Path $scriptRoot "..\artifacts\parity"
}

function Invoke-AdbBinary([string[]]$Arguments, [string]$OutputPath) {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = "adb"
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.Arguments = ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + $_.Replace('"', '\"') + '"'
        } else {
            $_
        }
    }) -join ' '
    [void]$process.Start()
    $file = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::Create)
    try {
        $process.StandardOutput.BaseStream.CopyTo($file)
    } finally {
        $file.Dispose()
    }
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "adb binary capture failed: $standardError"
    }
}

function Get-Aapt2 {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $localProperties = Join-Path $PSScriptRoot "..\local.properties"
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
            if ($sdkLine) { $sdkRoot = ($sdkLine -replace '^sdk.dir=', '').Replace('\:', ':').Replace('\\', '\') }
        }
    }
    $aapt = Get-ChildItem -Path (Join-Path $sdkRoot "build-tools") -Filter "aapt2.exe" -Recurse |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $aapt) { throw "aapt2.exe was not found." }
    return $aapt.FullName
}

function Read-UiHierarchy {
    # UiAutomation is a process-wide singleton on API 37. A preceding dump can take a little
    # longer than the adb shell process itself to unregister, so retry serially and never overlap
    # dumps. Returning an empty string is handled as INVALID by the scenario assertions.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        for ($attempt = 0; $attempt -lt 5; $attempt++) {
            $dumpOutput = @(& adb -s $Serial shell uiautomator dump /sdcard/car_launcher_parity_window.xml 2>&1)
            if ($LASTEXITCODE -eq 0) {
                $xml = (& adb -s $Serial exec-out cat /sdcard/car_launcher_parity_window.xml) -join ""
                if ($xml -match '<hierarchy') {
                    return $xml
                }
            }
            Start-Sleep -Seconds 2
        }
        return ""
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Dismiss-InitialUserNotice {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
    # The development AVD may show CarService's KitchenSink user notice on the first
    # activity launch. It belongs to the image, not Car Launcher, and would otherwise
    # obscure both golden screenshots. Detect it through the accessibility tree and
    # dismiss only when the exact button is present.
    function Read-NoticeTree { return Read-UiHierarchy }

        Start-Sleep -Milliseconds 750
        for ($attempt = 0; $attempt -lt 3; $attempt++) {
            $noticeXml = Read-NoticeTree
            if ([string]::IsNullOrWhiteSpace($noticeXml)) { return }
            # The API 37 development image renders the CarService notice from KitchenSink.
            # Tapping its "Dismiss for now" button launches the image's privacy activity and
            # steals HOME focus, which makes the capture invalid. Stop only that notice host and
            # relaunch the requested scenario instead of interacting with the product UI.
            if ($noticeXml -match 'package="com\.google\.android\.car\.kitchensink"') {
                & adb -s $Serial shell am force-stop --user $UserId com.google.android.car.kitchensink | Out-Null
                Start-Sleep -Milliseconds 750
                if (-not [string]::IsNullOrWhiteSpace($scenarioCommand)) {
                    & adb -s $Serial shell $scenarioCommand | Out-Null
                }
                return
            }
            $dismissNode = [regex]::Match($noticeXml, 'text="Dismiss for now"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
            if ($dismissNode.Success) {
                $left = [int]$dismissNode.Groups[1].Value
                $top = [int]$dismissNode.Groups[2].Value
                $right = [int]$dismissNode.Groups[3].Value
                $bottom = [int]$dismissNode.Groups[4].Value
                $x = [int](($left + $right) / 2)
                $y = [int](($top + $bottom) / 2)
                & adb -s $Serial shell input tap $x $y | Out-Null
                Start-Sleep -Milliseconds 1200
                continue
            }
            if ($noticeXml -notmatch "Dismiss for now") { return }
            Start-Sleep -Milliseconds 750
        }
        Start-Sleep -Milliseconds 750
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Stop-BackgroundScenarioTasks {
    # The development AVD may keep a previously selected Settings/MySystemApp task on top while
    # the snapshot is restored. Stop those external tasks before collecting a launcher golden.
    foreach ($package in @(
            "com.android.mysystemapp",
            "com.android.car.settings",
            "com.google.android.car.kitchensink",
            "com.android.car.carlauncher.fixture"
        )) {
        & adb -s $Serial shell am force-stop --user $UserId $package | Out-Null
    }
}

function Seed-FixtureMedia {
    if (-not $RequireFixtures) { return $false }
    # Give the fixture package a short foreground activity window before starting its service.
    # Android 37 rejects a background `am startservice`; the activity is then covered by HOME
    # while the service remains alive and publishes the same active session for both labels.
    $activityOutput = @(
        & adb -s $Serial shell am start --user $UserId -W `
            -n com.android.car.carlauncher.fixture/.FixtureMediaActivity 2>&1
    )
    if ($LASTEXITCODE -ne 0 -or ($activityOutput -join "`n") -match "Error") {
        return $false
    }
    $serviceOutput = @(
        & adb -s $Serial shell am startservice --user $UserId `
            -n com.android.car.carlauncher.fixture/.FixtureMediaBrowserService `
            -a com.android.car.carlauncher.fixture.action.MEDIA_PLAY 2>&1
    )
    if ($LASTEXITCODE -ne 0 -or ($serviceOutput -join "`n") -match "Error") {
        return $false
    }
    Start-Sleep -Milliseconds 500
    return $true
}

function Seed-FixtureTasks([string]$ScenarioName) {
    if (-not $RequireFixtures -or $ScenarioName -ne "recents") { return $true }
    # Recents must be exercised with real tasks. A single HOME/TaskView map is not a valid
    # QuickStep fixture because stock Recents immediately returns to that embedded task.
    $components = @(
        "com.android.car.carlauncher.fixture/.FixtureUtilityActivity",
        "com.android.car.carlauncher.fixture/.FixtureMediaActivity",
        "com.android.car.carlauncher.fixture/.FixtureMapActivity"
    )
    $expectedComponents = $components | ForEach-Object {
        $_ -replace "/\.", "/com.android.car.carlauncher.fixture."
    }
    foreach ($component in $components) {
        $startOutput = @(
            & adb -s $Serial shell am start --user $UserId -W -f 0x18000000 -n $component 2>&1
        )
        if ($LASTEXITCODE -ne 0 -or ($startOutput -join "`n") -match "Error") {
            return $false
        }
    }
    Start-Sleep -Milliseconds 500
    $activityDump = (& adb -s $Serial shell dumpsys activity activities) -join "`n"
    foreach ($component in $expectedComponents) {
        if ($activityDump -notmatch [regex]::Escape($component)) {
            return $false
        }
    }
    return $true
}

function Get-ScenarioComponent([string]$Name) {
    switch ($Name) {
        "home" { return "com.android.car.carlauncher/.CarLauncher" }
        "app-grid" { return "com.android.car.carlauncher/.AppGridActivity" }
        "recents" { return "com.android.car.carlauncher/.recents.CarRecentsActivity" }
        "calm-mode" { return "com.android.car.carlauncher/.calmmode.CalmModeActivity" }
        "widget-host" { return "com.android.car.carlauncher/.WidgetHostActivity" }
        "map-tos" { return "com.android.car.carlauncher/.homescreen.MapTosActivity" }
        default { return "" }
    }
}

function Test-ScenarioForeground([string]$Component, [string]$ActivityDump) {
    $topMatch = [regex]::Match($ActivityDump, "topResumedActivity=.*?\s(?<component>[^\s}]+/[^\s}]+)")
    if (-not $topMatch.Success) { return $false }
    $topComponent = $topMatch.Groups["component"].Value
    if ($topComponent -eq $Component) { return $true }

    # HOME owns the visible host while the controlled TaskView owns the top-resumed embedded map
    # task. AOSP reports the map activity in topResumedActivity on this API level, so accepting
    # only CarLauncher would mark a healthy TaskView host as INVALID. Require both the map task
    # and the launcher host task to be present; an unrelated map task cannot satisfy this rule.
    return $Scenario -eq "home" -and
        $topComponent -match "^com\.android\.(?:car\.mapsplaceholder|car\.maps)/" -and
        $ActivityDump -match "com\.android\.car\.carlauncher/\.CarLauncher"
}

function Ensure-ScenarioForeground([string]$ScenarioCommand, [string]$Component) {
    if ([string]::IsNullOrWhiteSpace($Component)) { return $true }
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $activityState = (& adb -s $Serial shell dumpsys activity activities) -join "`n"
        if (Test-ScenarioForeground $Component $activityState) { return $true }

        # HOME starts a controlled TaskView asynchronously. On API 37 the launcher activity
        # can be resumed while the embedded map transition is still collecting; issuing another
        # HOME intent in that window moves the map out of the host and creates a false timeout.
        # Let an in-flight HOME topology settle before retrying. Other scenarios retain the
        # shorter retry path because they have no embedded task transition to drain.
        if ($Scenario -eq "home" -and
            $activityState -match "com\.android\.car\.carlauncher/\.CarLauncher") {
            for ($settle = 0; $settle -lt 6; $settle++) {
                Start-Sleep -Seconds 1
                $activityState = (& adb -s $Serial shell dumpsys activity activities) -join "`n"
                if (Test-ScenarioForeground $Component $activityState) { return $true }
            }
        }
        if ($Scenario -eq "recents") {
            # Recents can spend several seconds moving the last task to the back. Do not
            # force-stop the fixture package during that transition; doing so destroys the
            # very tasks that make this a valid QuickStep scenario.
            Start-Sleep -Seconds 2
            continue
        }
        Stop-BackgroundScenarioTasks
        & adb -s $Serial shell $ScenarioCommand | Out-Null
        Start-Sleep -Seconds 1
    }
    return $false
}

function Test-FixturePrecondition {
    $fixtureComponent = "com.android.car.carlauncher.fixture/.FixtureMapActivity"
    $resolution = @(& adb -s $Serial shell cmd package resolve-activity --user $UserId --brief -n $fixtureComponent 2>&1)
    return $LASTEXITCODE -eq 0 -and (($resolution -join "`n") -match "com\.android\.car\.carlauncher\.fixture")
}

function Invoke-AdbTextWithTimeout([string[]]$Arguments, [int]$TimeoutMilliseconds = 5000) {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = "adb"
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.Arguments = ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + $_.Replace('"', '\\"') + '"'
        } else {
            $_
        }
    }) -join ' '
    [void]$process.Start()
    # Begin draining both pipes before waiting. `dumpsys window windows` is large enough to fill
    # a redirected stdout pipe; waiting first would make a healthy AVD look like a timeout.
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutMilliseconds)) {
        try { $process.Kill() } catch { }
        $process.WaitForExit()
        return [PSCustomObject]@{
            ExitCode = $null
            StandardOutput = $stdoutTask.Result
            StandardError = $stderrTask.Result
            TimedOut = $true
        }
    }
    return [PSCustomObject]@{
        ExitCode = $process.ExitCode
        StandardOutput = $stdoutTask.Result
        StandardError = $stderrTask.Result
        TimedOut = $false
    }
}

function Wait-ForStartingWindowGone {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while ([DateTime]::UtcNow -lt $deadline) {
        $windowResult = Invoke-AdbTextWithTimeout @("-s", $Serial, "shell", "dumpsys", "window", "windows")
        if ($windowResult.TimedOut -or $windowResult.ExitCode -ne 0) {
            return $false
        }
        if ($windowResult.StandardOutput -notmatch "Splash Screen com\.android\.car\.carlauncher") {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "APK is missing: $ApkPath"
}
if ((adb -s $Serial get-state).Trim() -ne "device") {
    throw "Android device is not ready: $Serial"
}
if ($Label -eq "baseline") {
    & (Join-Path $PSScriptRoot "verify-baseline.ps1") -ApkPath $ApkPath -VerifyDevice -Serial $Serial | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $ArtifactsRoot "$timestamp-$Label-$Scenario"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$scenarioComponent = Get-ScenarioComponent $Scenario
$scenarioCommand = $null
$foregroundMatched = $true
$fixtureReady = $true
$fixtureSeeded = $false
$fixtureTasksSeeded = $true
$instrumentationResult = [ordered]@{
    required = [bool]$RequireInstrumentation
    status = if ($RequireInstrumentation) { "INVALID" } else { "SKIPPED" }
    spec = $null
    exitCode = $null
    output = ""
    reason = if ($RequireInstrumentation) { "Instrumentation APK was not supplied." } else { "Not requested." }
}

if ($Install) {
    # The API 37 AVD's streamed PackageInstaller transport is unreliable. Using adb's
    # no-streaming transport preserves the same package-manager semantics and returns a result.
    # Both the stock Baklava APK and the release candidate use preview SDK metadata. The
    # PackageInstaller requires -t for preview/test-only transports; keeping it explicit makes
    # baseline and candidate installs deterministic instead of depending on adb heuristics.
    $installArguments = @("-s", $Serial, "install", "--no-streaming", "-t", "-r", "--user", "$UserId")
    if ($Label -eq "baseline") { $installArguments += "-d" }
    $installArguments += $ApkPath
    & adb @installArguments
    if ($LASTEXITCODE -ne 0) { throw "APK installation failed for $Label." }
}

if ($RequireFixtures) {
    $fixtureReady = Test-FixturePrecondition
}

if (-not [string]::IsNullOrWhiteSpace($InstrumentationApk)) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if (-not (Test-Path -LiteralPath $InstrumentationApk)) {
            $instrumentationResult.status = "INVALID"
            $instrumentationResult.reason = "Instrumentation APK is missing: $InstrumentationApk"
        } else {
            $installOutput = @(& adb -s $Serial install --no-streaming -t -r --user $UserId $InstrumentationApk 2>&1)
            if ($LASTEXITCODE -ne 0) {
                $instrumentationResult.status = "INVALID"
                $instrumentationResult.reason = "Instrumentation APK installation failed."
                $instrumentationResult.output = ($installOutput -join "`n")
            } else {
                $instrumentationList = @(& adb -s $Serial shell pm list instrumentation 2>&1)
                $instrumentationText = $instrumentationList -join "`n"
                $specMatch = [regex]::Match(
                    $instrumentationText,
                    "instrumentation:([^\s]+)\s+\(target=com\.android\.car\.carlauncher\)"
                )
                if (-not $specMatch.Success) {
                    $instrumentationResult.status = "INVALID"
                    $instrumentationResult.reason = "No instrumentation targeting com.android.car.carlauncher is installed."
                    $instrumentationResult.output = $instrumentationText
                } else {
                    $instrumentationResult.spec = $specMatch.Groups[1].Value
                }
            }
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

# Isolate the evidence window from installation output and stale processes. Clear every buffer;
# the default-buffer-only form leaves old ANR records on the API 37 image.
& adb -s $Serial shell logcat -b all -c | Out-Null

$aapt2 = Get-Aapt2
& $aapt2 dump badging $ApkPath | Set-Content -LiteralPath (Join-Path $outputDir "apk-badging.txt")
& $aapt2 dump xmltree $ApkPath --file AndroidManifest.xml | Set-Content -LiteralPath (Join-Path $outputDir "manifest.xmltree.txt")
& $aapt2 dump resources $ApkPath | Set-Content -LiteralPath (Join-Path $outputDir "resources.txt")

if ($LaunchScenario) {
    Stop-BackgroundScenarioTasks
    $fixtureSeeded = Seed-FixtureMedia
    $fixtureTasksSeeded = Seed-FixtureTasks $Scenario
    & adb -s $Serial shell am force-stop com.android.car.carlauncher
    $scenarioCommand = switch ($Scenario) {
        "home" {
            "am start --user $UserId -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.android.car.carlauncher/.CarLauncher"
        }
        "app-grid" {
            # Use the stable component for the visual scenario. The action resolution contract is
            # still captured below; explicit launch avoids an existing single-instance task or
            # TaskView stealing focus between baseline and candidate captures.
            "am start --user $UserId -W -n com.android.car.carlauncher/.AppGridActivity"
        }
        "recents" {
            "am start --user $UserId -W -a com.android.car.carlauncher.recents.OPEN_RECENT_TASK_ACTION -p com.android.car.carlauncher"
        }
        "calm-mode" {
            "am start --user $UserId -W -n com.android.car.carlauncher/.calmmode.CalmModeActivity"
        }
        "widget-host" {
            "am start --user $UserId -W -n com.android.car.carlauncher/.WidgetHostActivity"
        }
        "map-tos" {
            "am start --user $UserId -W -n com.android.car.carlauncher/.homescreen.MapTosActivity"
        }
    }
    & adb -s $Serial shell $scenarioCommand |
        Set-Content -LiteralPath (Join-Path $outputDir "launch-$Scenario.txt")
    Start-Sleep -Seconds 2
    Dismiss-InitialUserNotice
    $foregroundMatched = Ensure-ScenarioForeground $scenarioCommand $scenarioComponent
}

$commands = [ordered]@{
    "package.txt" = "dumpsys package com.android.car.carlauncher"
    "activity.txt" = "dumpsys activity activities"
    "window.txt" = "dumpsys window windows"
    "display.txt" = "dumpsys display"
    "home-resolution.txt" = "cmd package resolve-activity --user $UserId --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    "app-grid-resolution.txt" = "cmd package resolve-activity --user $UserId --brief -a com.android.car.carlauncher.ACTION_APP_GRID -p com.android.car.carlauncher"
    "quickstep-resolution.txt" = "cmd package query-services --user $UserId --brief -a android.intent.action.QUICKSTEP_SERVICE -p com.android.car.carlauncher"
}
foreach ($entry in $commands.GetEnumerator()) {
    & adb -s $Serial shell $entry.Value | Set-Content -LiteralPath (Join-Path $outputDir $entry.Key)
}

$activityDump = Get-Content -LiteralPath (Join-Path $outputDir "activity.txt") -Raw
$topResumedMatch = [regex]::Match($activityDump, "topResumedActivity=.*?\s(?<component>[^\s}]+/[^\s}]+)")
$taskMap = @{}
foreach ($taskMatch in [regex]::Matches($activityDump, "Task\{[^\r\n]*?#(?<taskId>\d+)[^\r\n]*?(?:displayId=(?<displayId>\d+))?")) {
    $taskId = [int]$taskMatch.Groups["taskId"].Value
    if (-not $taskMap.ContainsKey($taskId)) {
        $taskMap[$taskId] = [PSCustomObject]@{
            taskId = $taskId
            displayId = if ($taskMatch.Groups["displayId"].Success) {
                [int]$taskMatch.Groups["displayId"].Value
            } else {
                $null
            }
        }
    }
}
$taskRecords = @($taskMap.Values | Sort-Object taskId)
$mapTaskIds = @(
    [regex]::Matches(
        $activityDump,
        "com\.android\.(?:car\.mapsplaceholder|car\.maps)[^\r\n]*?t(?<taskId>\d+)"
    ) |
        ForEach-Object { $_.Groups["taskId"].Value } |
        Sort-Object -Unique
)
$mapTaskCount = $mapTaskIds.Count
$topologyValid = $foregroundMatched -and $mapTaskCount -le 1
$taskTopology = [ordered]@{
    topResumedActivity = if ($topResumedMatch.Success) { $topResumedMatch.Groups["component"].Value } else { $null }
    tasks = $taskRecords
    mapTaskCount = $mapTaskCount
    duplicateMapTaskFree = $mapTaskCount -le 1
}

# AOSP App Grid can report RESUMED before its GridView has completed the first expensive icon
# layout. Capture the accessibility tree first (the dump waits for the UI thread), validate a
# scenario-specific semantic marker, then take the screenshot. This prevents a stale HOME frame
# from being accepted as an App Grid golden.
$windowXml = Read-UiHierarchy
$windowXml | Set-Content -LiteralPath (Join-Path $outputDir "window.xml")
$initialNoticeVisible = $windowXml -match "Dismiss for now"
if ($initialNoticeVisible) {
    # The development image can show the notice only after the first expensive AOSP draw. The
    # earlier pre-launch dismissal is intentionally best effort; dismiss again after the semantic
    # dump so the golden never contains image/setup UI.
    Dismiss-InitialUserNotice
    Start-Sleep -Milliseconds 750
    $windowXml = Read-UiHierarchy
    $windowXml | Set-Content -LiteralPath (Join-Path $outputDir "window.xml")
}
$uiReady = switch ($Scenario) {
    "app-grid" { $windowXml -match "apps_grid" }
    "home" { $windowXml -match "CarLauncher|home|mapsplaceholder|aosp_nav" }
    "recents" { $windowXml -match "recents|Recents" }
    "calm-mode" { $windowXml -match "calm|Calm" }
    "widget-host" { $windowXml -match "widget|Widget" }
    "map-tos" { $windowXml -match "tos|TOS|Terms" }
    default { $windowXml.Length -gt 100 }
}
$noticeCleared = $windowXml -notmatch "Dismiss for now"
$startingWindowGone = Wait-ForStartingWindowGone
(& adb -s $Serial shell dumpsys window windows) | Set-Content -LiteralPath (Join-Path $outputDir "render-window.txt")
(& adb -s $Serial shell dumpsys activity activities) | Set-Content -LiteralPath (Join-Path $outputDir "render-activity.txt")
Start-Sleep -Milliseconds 750
# SurfaceFlinger on this AVD can return the pre-transaction buffer for the first display capture
# while the stock launcher is finishing its expensive GridView draw. Prime one capture and use the
# following frame as evidence; the prime is retained for diagnosis but is never compared.
Invoke-AdbBinary @("-s", $Serial, "exec-out", "screencap", "-p") (Join-Path $outputDir "screen-prime.png")
Start-Sleep -Milliseconds 500
Invoke-AdbBinary @("-s", $Serial, "exec-out", "screencap", "-p") (Join-Path $outputDir "screen.png")

# Re-read the task/window topology after the evidence frame. A launcher can lose focus after
# the pre-capture dump (for example when a fixture task or a starting window finishes), which
# would otherwise allow a screenshot of the wrong activity to be accepted. Keep both the initial
# and final dumps so a failed run is diagnosable and fail closed on the final foreground owner.
$finalActivityResult = Invoke-AdbTextWithTimeout @("-s", $Serial, "shell", "dumpsys", "activity", "activities")
$finalActivityDump = if ($finalActivityResult.TimedOut) { "" } else { $finalActivityResult.StandardOutput }
$finalActivityDump | Set-Content -LiteralPath (Join-Path $outputDir "render-activity-final.txt")
$finalWindowResult = Invoke-AdbTextWithTimeout @("-s", $Serial, "shell", "dumpsys", "window", "windows")
$finalWindowDump = if ($finalWindowResult.TimedOut) { "" } else { $finalWindowResult.StandardOutput }
$finalWindowDump | Set-Content -LiteralPath (Join-Path $outputDir "render-window-final.txt")
$finalTopResumedMatch = [regex]::Match(
    $finalActivityDump,
    "topResumedActivity=.*?\s(?<component>[^\s}]+/[^\s}]+)"
)
$finalForegroundMatched =
    -not $finalActivityResult.TimedOut -and
    (Test-ScenarioForeground $scenarioComponent $finalActivityDump)
$finalMapTaskIds = @(
    [regex]::Matches(
        $finalActivityDump,
        "com\.android\.(?:car\.mapsplaceholder|car\.maps)[^\r\n]*?t(?<taskId>\d+)"
    ) |
        ForEach-Object { $_.Groups["taskId"].Value } |
        Sort-Object -Unique
)
$finalMapTaskCount = $finalMapTaskIds.Count
$finalTopologyValid =
    $finalForegroundMatched -and
    -not $finalActivityResult.TimedOut -and
    $finalMapTaskCount -le 1
$foregroundMatched = $foregroundMatched -and $finalForegroundMatched
$topologyValid = $topologyValid -and $finalTopologyValid
& adb -s $Serial shell logcat -b all -d -v threadtime | Select-Object -Last 3000 |
    Set-Content -LiteralPath (Join-Path $outputDir "logcat.txt")

$logLines = @(Get-Content -LiteralPath (Join-Path $outputDir "logcat.txt") -ErrorAction SilentlyContinue)
# UiAutomation can emit a known singleton-registration FATAL while its previous shell process is
# unregistering. It is harness noise, not an app crash; retain it in logcat but exclude the whole
# matching crash block from the candidate health gate. All other FATAL/ANR/SecurityException
# entries remain fail-closed. The FATAL line itself does not contain the UiAutomation message, so
# filtering only matching lines would incorrectly invalidate an otherwise healthy capture.
$uiAutomationFatalIndexes = @()
for ($lineIndex = 0; $lineIndex -lt $logLines.Count; $lineIndex++) {
    if ($logLines[$lineIndex] -notmatch "FATAL EXCEPTION") { continue }
    $blockEnd = [Math]::Min($logLines.Count - 1, $lineIndex + 40)
    $block = ($logLines[$lineIndex..$blockEnd] -join "`n")
    if ($block -match "UiAutomationService .*already registered") {
        $uiAutomationFatalIndexes += $lineIndex
    }
}
$effectiveLogLines = @()
for ($lineIndex = 0; $lineIndex -lt $logLines.Count; $lineIndex++) {
    if ($uiAutomationFatalIndexes -contains $lineIndex) { continue }
    if ($logLines[$lineIndex] -match "UiAutomationService .*already registered") { continue }
    if ($logLines[$lineIndex] -match "UiAutomationConnection\.registerUiTestAutomationService") { continue }
    $effectiveLogLines += $logLines[$lineIndex]
}
$fatalCount = @($effectiveLogLines | Select-String -Pattern "FATAL EXCEPTION|Fatal signal|Process .* has died").Count
$uiAutomationNoiseCount = $logLines.Count - $effectiveLogLines.Count
$anrCount = @($effectiveLogLines | Select-String -Pattern "ANR in |Input dispatching timed out").Count
$securityExceptionCount = @($effectiveLogLines | Select-String -Pattern "SecurityException").Count
$instrumentationPassed = $true
if (-not [string]::IsNullOrWhiteSpace($instrumentationResult.spec)) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $instrumentationOutput = @(& adb -s $Serial shell am instrument -w -r $instrumentationResult.spec 2>&1)
        $instrumentationResult.exitCode = $LASTEXITCODE
        $instrumentationResult.output = ($instrumentationOutput -join "`n")
        $instrumentationPassed =
            $LASTEXITCODE -eq 0 -and
            $instrumentationResult.output -notmatch "INSTRUMENTATION_FAILED|FAILURES!!!"
        $instrumentationResult.status = if ($instrumentationPassed) { "PASS" } else { "FAIL" }
        if (-not $instrumentationPassed) {
            $instrumentationResult.reason = "Instrumentation reported a failure."
        } else {
            $instrumentationResult.reason = ""
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}
$status = if (-not $foregroundMatched -or -not $topologyValid -or -not $fixtureReady -or
    ($RequireFixtures -and -not $fixtureSeeded) -or
    ($RequireFixtures -and $Scenario -eq "recents" -and -not $fixtureTasksSeeded) -or
    -not $uiReady -or -not $noticeCleared -or -not $startingWindowGone -or
    ($RequireInstrumentation -and (-not $instrumentationPassed -or $instrumentationResult.status -ne "PASS")) -or
    $fatalCount -gt 0 -or $anrCount -gt 0 -or $securityExceptionCount -gt 0) {
    "INVALID"
} else {
    "PASS"
}
$statusReason = if (-not $fixtureReady) {
    "Required fixture APK is not resolvable for the requested user."
} elseif ($RequireFixtures -and -not $fixtureSeeded) {
    "Deterministic fixture media session could not be seeded before the scenario."
} elseif ($RequireFixtures -and $Scenario -eq "recents" -and -not $fixtureTasksSeeded) {
    "Recents fixture tasks could not be created before launching QuickStep."
} elseif (-not $topologyValid) {
    "Task topology is invalid: the expected foreground or duplicate map-task invariant failed."
} elseif (-not $foregroundMatched) {
    "Expected scenario component was not top-resumed after retries."
} elseif (-not $uiReady) {
    "Scenario semantic UI marker was not present when the hierarchy was captured."
} elseif (-not $noticeCleared) {
    "Development AVD initial user notice remained visible after dismissal."
} elseif (-not $startingWindowGone) {
    "Launcher starting window remained visible after the render timeout."
} elseif ($RequireInstrumentation -and $instrumentationResult.status -ne "PASS") {
    $instrumentationResult.reason
} elseif ($fatalCount -gt 0 -or $anrCount -gt 0 -or $securityExceptionCount -gt 0) {
    "Scenario log window contains a fatal, ANR, or security failure."
} else {
    ""
}
$actionList = @()
if ($fixtureSeeded) {
    $actionList += "am start --user $UserId -W -n com.android.car.carlauncher.fixture/.FixtureMediaActivity"
    $actionList += "am startservice --user $UserId -n com.android.car.carlauncher.fixture/.FixtureMediaBrowserService -a com.android.car.carlauncher.fixture.action.MEDIA_PLAY"
}
if ($fixtureTasksSeeded -and $Scenario -eq "recents") {
    $actionList += "am start --user $UserId -W -f 0x18000000 -n com.android.car.carlauncher.fixture/.FixtureUtilityActivity"
    $actionList += "am start --user $UserId -W -f 0x18000000 -n com.android.car.carlauncher.fixture/.FixtureMediaActivity"
    $actionList += "am start --user $UserId -W -f 0x18000000 -n com.android.car.carlauncher.fixture/.FixtureMapActivity"
}
if ($null -ne $scenarioCommand) {
    $actionList += $scenarioCommand
}

[PSCustomObject]@{
    schemaVersion = 2
    label = $Label
    apk = (Resolve-Path -LiteralPath $ApkPath).Path
    installed = [bool]$Install
    scenario = $Scenario
    launchedScenario = [bool]$LaunchScenario
    serial = $Serial
    userId = $UserId
    status = $status
    statusReason = $statusReason
    scenarioComponent = $scenarioComponent
    preconditions = [ordered]@{
        deviceReady = $true
        userId = $UserId
        snapshotRequired = $true
        installRequested = [bool]$Install
        fixtureReady = $fixtureReady
        fixtureSeeded = $fixtureSeeded
        fixtureTasksSeeded = $fixtureTasksSeeded
        instrumentationRequired = [bool]$RequireInstrumentation
        instrumentationApk = if ([string]::IsNullOrWhiteSpace($InstrumentationApk)) { $null } else { (Resolve-Path -LiteralPath $InstrumentationApk -ErrorAction SilentlyContinue).Path }
    }
    actions = $actionList
    assertions = [ordered]@{
        foregroundComponent = $scenarioComponent
        foregroundMatched = $foregroundMatched
        finalForegroundMatched = $finalForegroundMatched
        fixtureReady = $fixtureReady
        fixtureTasksSeeded = $fixtureTasksSeeded
        topologyValid = $topologyValid
        finalTopologyValid = $finalTopologyValid
        uiReady = $uiReady
        noticeCleared = $noticeCleared
        startingWindowGone = $startingWindowGone
        fatalCount = $fatalCount
        anrCount = $anrCount
        securityExceptionCount = $securityExceptionCount
        uiAutomationNoiseCount = $uiAutomationNoiseCount
    }
    taskTopology = $taskTopology
    finalTaskTopology = [ordered]@{
        topResumedActivity = if ($finalTopResumedMatch.Success) {
            $finalTopResumedMatch.Groups["component"].Value
        } else { $null }
        mapTaskCount = $finalMapTaskCount
        duplicateMapTaskFree = $finalMapTaskCount -le 1
        activityDump = "render-activity-final.txt"
        windowDump = "render-window-final.txt"
    }
    instrumentation = $instrumentationResult
    logWindow = [ordered]@{
        file = "logcat.txt"
        lines = $logLines.Count
        clearedBeforeLaunch = [bool]$LaunchScenario
    }
    outputDir = (Resolve-Path -LiteralPath $outputDir).Path
    capturedAt = (Get-Date).ToUniversalTime().ToString("o")
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $outputDir "capture.json")

Write-Output (Resolve-Path -LiteralPath $outputDir).Path
