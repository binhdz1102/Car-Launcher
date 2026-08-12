[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ApkPath,
    [string]$LockFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($LockFile)) {
    $LockFile = Join-Path $repoRoot "baseline.lock.json"
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "APK is missing: $ApkPath"
}
$lock = Get-Content -LiteralPath $LockFile -Raw | ConvertFrom-Json
$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $sdkLine = Get-Content -LiteralPath (Join-Path $repoRoot "local.properties") |
        Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
    if ($sdkLine) { $sdkRoot = ($sdkLine -replace '^sdk.dir=', '').Replace('\:', ':').Replace('\\', '\') }
}
$buildTools = Get-ChildItem -Path (Join-Path $sdkRoot "build-tools") -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
$aapt2 = Join-Path $buildTools.FullName "aapt2.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$badging = & $aapt2 dump badging $ApkPath
$packageLine = $badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
if ($packageLine -notmatch "name='$([regex]::Escape($lock.baseline.packageName))'") {
    throw "APK package does not match the launcher contract: $packageLine"
}
$certificateLine = (& $apksigner verify --print-certs $ApkPath) |
    Where-Object { $_ -like "*certificate SHA-256 digest:*" } | Select-Object -First 1
$certificateMatch = [regex]::Match($certificateLine, "([0-9a-fA-F]{64})$")
if (-not $certificateMatch.Success) {
    throw "Unable to read release certificate digest."
}
$certificateSha = $certificateMatch.Groups[1].Value.ToLowerInvariant()
if ($certificateSha -ne $lock.baseline.certificateSha256) {
    throw "Release APK is not signed by the target platform certificate."
}

[PSCustomObject]@{
    apk = (Resolve-Path -LiteralPath $ApkPath).Path
    sha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    packageName = $lock.baseline.packageName
    certificateSha256 = $certificateSha
} | ConvertTo-Json
