[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$Serial = "emulator-5554",
    [int]$UserId = 10,
    [int]$TimeoutSeconds = 120,
    [switch]$Build,
    [switch]$RestoreBaseline,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$packageName = "com.android.car.carlauncher"

function Invoke-Adb([string[]]$Arguments) {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = "adb"
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.Arguments = ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') { '"' + $_.Replace('"', '\"') + '"' } else { $_ }
    }) -join ' '
    [void]$process.Start()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill($true)
        throw "adb timed out after $TimeoutSeconds seconds: $($process.StartInfo.Arguments)"
    }
    $stdout = $process.StandardOutput.ReadToEnd().Trim()
    $stderr = $process.StandardError.ReadToEnd().Trim()
    if ($process.ExitCode -ne 0) {
        throw "adb failed ($($process.ExitCode)): $stdout $stderr"
    }
    return $stdout
}

if ($RestoreBaseline) {
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        $ApkPath = Join-Path $repoRoot "Launcher\apk\CarLauncher.apk"
    }
    & (Join-Path $scriptRoot "verify-baseline.ps1") -ApkPath $ApkPath -VerifyDevice -Serial $Serial | Out-Null
    $installArguments = @("-s", $Serial, "install", "-r", "-d", $ApkPath)
} else {
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        $ApkPath = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    }
    if ($Build) {
        & (Join-Path $scriptRoot "verify-platform-artifacts.ps1") -VerifyDevice -Serial $Serial | Out-Null
        & (Join-Path $repoRoot "gradlew.bat") :app:assembleRelease --console=plain --max-workers=2
        if ($LASTEXITCODE -ne 0) { throw "Release build failed." }
    }
    & (Join-Path $scriptRoot "verify-release-apk.ps1") -ApkPath $ApkPath | Out-Null
    $installArguments = @("-s", $Serial, "install", "-r", $ApkPath)
}

if ($DryRun) {
    [PSCustomObject]@{
        restoreBaseline = [bool]$RestoreBaseline
        apk = $ApkPath
        command = "adb " + ($installArguments -join " ")
    } | ConvertTo-Json
    exit 0
}

if ((Invoke-Adb @("-s", $Serial, "get-state")) -ne "device") {
    throw "Android device is not ready: $Serial"
}
Invoke-Adb @("-s", $Serial, "shell", "am", "force-stop", "--user", "$UserId", $packageName) | Out-Null
Invoke-Adb $installArguments | Out-Null

$packagePath = Invoke-Adb @("-s", $Serial, "shell", "pm", "path", "--user", "$UserId", $packageName)
if (-not $RestoreBaseline -and $packagePath -notlike "package:/data/app/*") {
    throw "Candidate did not become the active update: $packagePath"
}
Invoke-Adb @(
    "-s", $Serial, "shell", "am", "start", "--user", "$UserId", "-W",
    "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME",
    "-n", "$packageName/.CarLauncher"
) | Out-Null

[PSCustomObject]@{
    restoredBaseline = [bool]$RestoreBaseline
    apk = (Resolve-Path -LiteralPath $ApkPath).Path
    activePath = $packagePath
    serial = $Serial
    userId = $UserId
} | ConvertTo-Json
