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
    [switch]$Install,
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

if ($Install) {
    $installArguments = @("-s", $Serial, "install", "-r", "--user", "$UserId")
    if ($Label -eq "baseline") { $installArguments += "-d" }
    $installArguments += $ApkPath
    & adb @installArguments
    if ($LASTEXITCODE -ne 0) { throw "APK installation failed for $Label." }
}

$aapt2 = Get-Aapt2
& $aapt2 dump badging $ApkPath | Set-Content -LiteralPath (Join-Path $outputDir "apk-badging.txt")
& $aapt2 dump xmltree $ApkPath --file AndroidManifest.xml | Set-Content -LiteralPath (Join-Path $outputDir "manifest.xmltree.txt")

if ($LaunchScenario) {
    & adb -s $Serial shell am force-stop com.android.car.carlauncher
    $scenarioCommand = switch ($Scenario) {
        "home" {
            "am start --user $UserId -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.android.car.carlauncher/.CarLauncher"
        }
        "app-grid" {
            "am start --user $UserId -W -a com.android.car.carlauncher.ACTION_APP_GRID -p com.android.car.carlauncher"
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

Invoke-AdbBinary @("-s", $Serial, "exec-out", "screencap", "-p") (Join-Path $outputDir "screen.png")
& adb -s $Serial shell uiautomator dump /sdcard/window.xml | Out-Null
& adb -s $Serial exec-out cat /sdcard/window.xml | Set-Content -LiteralPath (Join-Path $outputDir "window.xml")
& adb -s $Serial logcat -d -v threadtime | Select-Object -Last 3000 |
    Set-Content -LiteralPath (Join-Path $outputDir "logcat.txt")

[PSCustomObject]@{
    label = $Label
    apk = (Resolve-Path -LiteralPath $ApkPath).Path
    installed = [bool]$Install
    scenario = $Scenario
    launchedScenario = [bool]$LaunchScenario
    serial = $Serial
    userId = $UserId
    outputDir = (Resolve-Path -LiteralPath $outputDir).Path
    capturedAt = (Get-Date).ToUniversalTime().ToString("o")
} | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $outputDir "capture.json")

Write-Output (Resolve-Path -LiteralPath $outputDir).Path
