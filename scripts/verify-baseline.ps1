[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$LockFile,
    [string]$PropertiesFile,
    [switch]$VerifyDevice,
    [string]$Serial = "emulator-5554"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($LockFile)) {
    $LockFile = Join-Path $scriptRoot "..\baseline.lock.json"
}
if ([string]::IsNullOrWhiteSpace($PropertiesFile)) {
    $PropertiesFile = Join-Path $scriptRoot "..\baseline.properties"
}

function Read-KeyValueFile([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }
        $parts = $trimmed.Split("=", 2)
        if ($parts.Length -eq 2) {
            $values[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
    return $values
}

function Get-Aapt2 {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = $env:ANDROID_HOME
    }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $localProperties = Join-Path $PSScriptRoot "..\local.properties"
        if (Test-Path -LiteralPath $localProperties) {
            $entry = Read-KeyValueFile $localProperties
            $sdkRoot = $entry["sdk.dir"]
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = $sdkRoot.Replace('\:', ':').Replace('\\', '\')
    }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        throw "Set ANDROID_SDK_ROOT or sdk.dir in local.properties to locate aapt2."
    }
    $aapt2 = Get-ChildItem -Path (Join-Path $sdkRoot "build-tools") -Filter "aapt2.exe" -Recurse |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $aapt2) {
        throw "aapt2.exe was not found below $sdkRoot\\build-tools."
    }
    return $aapt2.FullName
}

if (-not (Test-Path -LiteralPath $LockFile)) {
    throw "Baseline lock file is missing: $LockFile"
}
$lock = Get-Content -LiteralPath $LockFile -Raw | ConvertFrom-Json
$properties = Read-KeyValueFile $PropertiesFile
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = $properties["baselineApk"]
}
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    $ApkPath = Join-Path $repoRoot $lock.baseline.pathHint
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "Baseline APK is missing: $ApkPath"
}

$actualSha = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSha -ne $lock.baseline.sha256) {
    throw "Baseline SHA-256 mismatch. Expected $($lock.baseline.sha256), got $actualSha."
}

$aapt2 = Get-Aapt2
$badging = & $aapt2 dump badging $ApkPath
$packageLine = $badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
if ($packageLine -notmatch "name='$([regex]::Escape($lock.baseline.packageName))'") {
    throw "Baseline package mismatch: $packageLine"
}
if ($packageLine -notmatch "versionCode='$($lock.baseline.versionCode)'") {
    throw "Baseline versionCode mismatch: $packageLine"
}
if ($packageLine -notmatch "versionName='$([regex]::Escape($lock.baseline.versionName))'") {
    throw "Baseline versionName mismatch: $packageLine"
}

$apksigner = Join-Path (Split-Path $aapt2 -Parent) "apksigner.bat"
if (-not (Test-Path -LiteralPath $apksigner)) {
    throw "apksigner.bat was not found beside $aapt2"
}
$certificateOutput = & $apksigner verify --print-certs $ApkPath
$certificateLine = $certificateOutput |
    Where-Object { $_ -like "Signer #1 certificate SHA-256 digest:*" } |
    Select-Object -First 1
if ($certificateLine -notmatch "([0-9a-fA-F]{64})$") {
    throw "Unable to read the baseline signing certificate digest."
}
$certificateSha = $Matches[1].ToLowerInvariant()
if ($certificateSha -ne $lock.baseline.certificateSha256) {
    throw "Baseline certificate mismatch. Expected $($lock.baseline.certificateSha256), got $certificateSha."
}

if ($VerifyDevice) {
    $fingerprint = (adb -s $Serial shell getprop ro.build.fingerprint).Trim()
    if ($fingerprint -ne $lock.target.buildFingerprint) {
        throw "AVD fingerprint mismatch. Expected $($lock.target.buildFingerprint), got $fingerprint."
    }
    $apiLevel = (adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    if ($apiLevel -ne "$($lock.target.apiLevel)") {
        throw "AVD API mismatch. Expected $($lock.target.apiLevel), got $apiLevel."
    }
}

[PSCustomObject]@{
    apkPath = (Resolve-Path -LiteralPath $ApkPath).Path
    sha256 = $actualSha
    packageName = $lock.baseline.packageName
    versionCode = $lock.baseline.versionCode
    versionName = $lock.baseline.versionName
    certificateSha256 = $certificateSha
    deviceVerified = [bool]$VerifyDevice
} | ConvertTo-Json -Depth 3
