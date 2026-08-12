[CmdletBinding()]
param(
    [string]$ArtifactsDirectory,
    [string]$LockFile,
    [switch]$VerifyDevice,
    [string]$Serial = "emulator-5554"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($LockFile)) {
    $LockFile = Join-Path $repoRoot "platform-artifacts.lock.json"
}
if ([string]::IsNullOrWhiteSpace($ArtifactsDirectory)) {
    $propertiesFile = Join-Path $repoRoot "platform-artifacts.properties"
    $configuredDirectory =
        if (Test-Path -LiteralPath $propertiesFile) {
            Get-Content -LiteralPath $propertiesFile |
                Where-Object { $_ -match '^platformArtifactsDir=' } |
                Select-Object -First 1
        } else {
            $null
        }
    $ArtifactsDirectory =
        if ($configuredDirectory) {
            $configuredDirectory -replace '^platformArtifactsDir=', ''
        } else {
            Join-Path $repoRoot "platform-artifacts\api-37"
        }
    if (-not [System.IO.Path]::IsPathRooted($ArtifactsDirectory)) {
        $ArtifactsDirectory = Join-Path $repoRoot $ArtifactsDirectory
    }
}
if (-not (Test-Path -LiteralPath $LockFile)) {
    throw "Platform artifact lock is missing: $LockFile"
}

$lock = Get-Content -LiteralPath $LockFile -Raw | ConvertFrom-Json
if ($lock.apiLevel -ne 37) {
    throw "The platform artifact lock must target API 37."
}
$errors = @()
foreach ($artifact in $lock.artifacts) {
    $path = Join-Path $ArtifactsDirectory $artifact.name
    if ($artifact.sha256 -like "PENDING_*") {
        $errors += "$($artifact.name) is not pinned; run sync-platform-artifacts.ps1 from the matching AOSP output."
    } elseif (-not (Test-Path -LiteralPath $path)) {
        $errors += "Missing $($artifact.name): $path"
    } else {
        $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $artifact.sha256) {
            $errors += "$($artifact.name) checksum mismatch."
        }
    }
}
if ($errors.Count -gt 0) {
    throw "Invalid API 37 platform artifact bundle:`n$($errors -join "`n")"
}
if ($VerifyDevice) {
    $fingerprint = (adb -s $Serial shell getprop ro.build.fingerprint).Trim()
    if ($fingerprint -ne $lock.buildFingerprint) {
        throw "AVD fingerprint mismatch. Expected $($lock.buildFingerprint), got $fingerprint."
    }
    $apiLevel = (adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    if ($apiLevel -ne "37") {
        throw "AVD API mismatch. Expected 37, got $apiLevel."
    }
}

[PSCustomObject]@{
    apiLevel = $lock.apiLevel
    artifactsDirectory = (Resolve-Path -LiteralPath $ArtifactsDirectory).Path
    artifactCount = @($lock.artifacts).Count
    deviceVerified = [bool]$VerifyDevice
} | ConvertTo-Json
