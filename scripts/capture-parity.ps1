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

function Dismiss-InitialUserNotice {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
    # The development AVD may show CarService's KitchenSink user notice on the first
    # activity launch. It belongs to the image, not Car Launcher, and would otherwise
    # obscure both golden screenshots. Detect it through the accessibility tree and
    # dismiss only when the exact button is present.
    function Read-NoticeTree {
        for ($retry = 0; $retry -lt 3; $retry++) {
            $dumpOutput = @(& adb -s $Serial shell uiautomator dump /sdcard/car_launcher_parity_notice.xml 2>&1)
            if ($LASTEXITCODE -eq 0) {
                $noticeXml = (& adb -s $Serial exec-out cat /sdcard/car_launcher_parity_notice.xml) -join ""
                if ($noticeXml -match '<hierarchy') { return $noticeXml }
            }
            # UiAutomation is a singleton on this AVD. Give the shell service time to unregister
            # before retrying instead of creating overlapping dumps and crashing uiautomator.
            Start-Sleep -Milliseconds 1000
        }
        return ""
    }

        Start-Sleep -Milliseconds 750
        for ($attempt = 0; $attempt -lt 3; $attempt++) {
            $noticeXml = Read-NoticeTree
            if ([string]::IsNullOrWhiteSpace($noticeXml)) { return }
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

function Ensure-ScenarioForeground([string]$ScenarioCommand, [string]$Component) {
    if ([string]::IsNullOrWhiteSpace($Component)) { return $true }
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $activityState = (& adb -s $Serial shell dumpsys activity activities) -join "`n"
        if ($activityState -match "topResumedActivity=.*$([regex]::Escape($Component))") { return $true }
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
    $installArguments = @("-s", $Serial, "install", "--no-streaming", "-r", "--user", "$UserId")
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
            $installOutput = @(& adb -s $Serial install --no-streaming -r --user $UserId $InstrumentationApk 2>&1)
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

Invoke-AdbBinary @("-s", $Serial, "exec-out", "screencap", "-p") (Join-Path $outputDir "screen.png")
& adb -s $Serial shell uiautomator dump /sdcard/window.xml | Out-Null
& adb -s $Serial exec-out cat /sdcard/window.xml | Set-Content -LiteralPath (Join-Path $outputDir "window.xml")
& adb -s $Serial shell logcat -b all -d -v threadtime | Select-Object -Last 3000 |
    Set-Content -LiteralPath (Join-Path $outputDir "logcat.txt")

$logLines = @(Get-Content -LiteralPath (Join-Path $outputDir "logcat.txt") -ErrorAction SilentlyContinue)
$fatalCount = @($logLines | Select-String -Pattern "FATAL EXCEPTION|Fatal signal|Process .* has died").Count
$anrCount = @($logLines | Select-String -Pattern "ANR in |Input dispatching timed out").Count
$securityExceptionCount = @($logLines | Select-String -Pattern "SecurityException").Count
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
    ($RequireInstrumentation -and (-not $instrumentationPassed -or $instrumentationResult.status -ne "PASS")) -or
    $fatalCount -gt 0 -or $anrCount -gt 0 -or $securityExceptionCount -gt 0) {
    "INVALID"
} else {
    "PASS"
}
$statusReason = if (-not $fixtureReady) {
    "Required fixture APK is not resolvable for the requested user."
} elseif (-not $topologyValid) {
    "Task topology is invalid: the expected foreground or duplicate map-task invariant failed."
} elseif (-not $foregroundMatched) {
    "Expected scenario component was not top-resumed after retries."
} elseif ($RequireInstrumentation -and $instrumentationResult.status -ne "PASS") {
    $instrumentationResult.reason
} elseif ($fatalCount -gt 0 -or $anrCount -gt 0 -or $securityExceptionCount -gt 0) {
    "Scenario log window contains a fatal, ANR, or security failure."
} else {
    ""
}
$actionList = if ($null -eq $scenarioCommand) { @() } else { @($scenarioCommand) }

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
        instrumentationRequired = [bool]$RequireInstrumentation
        instrumentationApk = if ([string]::IsNullOrWhiteSpace($InstrumentationApk)) { $null } else { (Resolve-Path -LiteralPath $InstrumentationApk -ErrorAction SilentlyContinue).Path }
    }
    actions = $actionList
    assertions = [ordered]@{
        foregroundComponent = $scenarioComponent
        foregroundMatched = $foregroundMatched
        fixtureReady = $fixtureReady
        topologyValid = $topologyValid
        fatalCount = $fatalCount
        anrCount = $anrCount
        securityExceptionCount = $securityExceptionCount
    }
    taskTopology = $taskTopology
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
