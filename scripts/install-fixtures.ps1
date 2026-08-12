[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$Serial = "emulator-5554",
    [int]$UserId = 10,
    [int]$TimeoutSeconds = 90,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path

function ConvertTo-ProcessArguments([string[]]$Arguments) {
    return ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + $_.Replace('"', '\"') + '"'
        } else {
            $_
        }
    }) -join ' '
}

function Invoke-Adb([string[]]$Arguments) {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = "adb"
    $process.StartInfo.Arguments = ConvertTo-ProcessArguments $Arguments
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill()
        $process.WaitForExit()
        throw "adb timed out after $TimeoutSeconds seconds: adb $($process.StartInfo.Arguments)"
    }
    $result = [PSCustomObject]@{
        exitCode = $process.ExitCode
        stdout = $stdout.GetAwaiter().GetResult().Trim()
        stderr = $stderr.GetAwaiter().GetResult().Trim()
    }
    if ($result.exitCode -ne 0) {
        throw "adb failed: $($result.stderr)"
    }
    return $result
}

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $repoRoot "test-apps\fixture-app\build\outputs\apk\debug\fixture-app-debug.apk"
}
if (-not $SkipBuild) {
    & (Join-Path $repoRoot "gradlew.bat") ":test-apps:fixture-app:assembleDebug" "--console=plain" "--quiet"
    if ($LASTEXITCODE -ne 0) {
        throw "Fixture APK build failed."
    }
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "Fixture APK is missing: $ApkPath"
}

$state = Invoke-Adb @("-s", $Serial, "get-state")
if ($state.stdout -ne "device") {
    throw "Android device is not ready: $Serial"
}

$fixturePackage = "com.android.car.carlauncher.fixture"
$install = Invoke-Adb @(
    "-s", $Serial,
    "install", "--no-streaming", "-r", "-t", "--user", "$UserId", $ApkPath
)
$components = @(
    "$fixturePackage/.FixtureMapActivity",
    "$fixturePackage/.FixtureMediaActivity",
    "$fixturePackage/.FixtureUtilityActivity",
    "$fixturePackage/.FixtureRestrictedActivity"
)
foreach ($component in $components) {
    $resolution = Invoke-Adb @(
        "-s", $Serial,
        "shell", "cmd", "package", "resolve-activity", "--user", "$UserId", "--brief", "-n", $component
    )
    if ($resolution.stdout -notmatch [regex]::Escape($fixturePackage)) {
        throw "Fixture component did not resolve for user ${UserId}: $component"
    }
}

[PSCustomObject]@{
    apk = (Resolve-Path -LiteralPath $ApkPath).Path
    sha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    installOutput = $install.stdout
    packageName = $fixturePackage
    serial = $Serial
    userId = $UserId
    components = $components
} | ConvertTo-Json -Depth 3
