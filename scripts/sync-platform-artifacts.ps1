[CmdletBinding()]
param(
    [string]$AospOut,
    [string]$PlatformLibrariesDirectory,
    [string]$ArtifactsDirectory,
    [string]$LockFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($AospOut)) {
    $AospOut = Join-Path $repoRoot "..\..\out-avd-car-mysystemapp"
}
if ([string]::IsNullOrWhiteSpace($PlatformLibrariesDirectory)) {
    $PlatformLibrariesDirectory = Join-Path $repoRoot "..\My-System-App\libs\platform"
}
if ([string]::IsNullOrWhiteSpace($ArtifactsDirectory)) {
    $ArtifactsDirectory = Join-Path $repoRoot "platform-artifacts\api-37"
}
if ([string]::IsNullOrWhiteSpace($LockFile)) {
    $LockFile = Join-Path $repoRoot "platform-artifacts.lock.json"
}

$sources = [ordered]@{
    "android.car.jar" = Join-Path $PlatformLibrariesDirectory "android.car.jar"
    "framework.jar" = Join-Path $PlatformLibrariesDirectory "framework.jar"
    "systemui-shared.jar" = Join-Path $AospOut "soong\.intermediates\frameworks\base\packages\SystemUI\shared\SystemUISharedLib\android_common\javac\SystemUISharedLib.jar"
    "systemui-shared-kotlin.jar" = Join-Path $AospOut "soong\.intermediates\frameworks\base\packages\SystemUI\shared\SystemUISharedLib\android_common\kotlin\SystemUISharedLib.jar"
    "wm-shell-aidls.jar" = Join-Path $AospOut "soong\.intermediates\frameworks\base\libs\WindowManager\Shell\WindowManager-Shell-aidls\android_common\javac\WindowManager-Shell-aidls.jar"
    "wm-shell-shared.jar" = Join-Path $AospOut "soong\.intermediates\frameworks\base\libs\WindowManager\Shell\shared\WindowManager-Shell-shared\android_common\javac\WindowManager-Shell-shared.jar"
    "car-qc-lib.jar" = Join-Path $AospOut "soong\.intermediates\packages\apps\Car\systemlibs\car-qc-lib\car-qc-lib\android_common\javac\car-qc-lib.jar"
}
$missing = $sources.GetEnumerator() | Where-Object { -not (Test-Path -LiteralPath $_.Value) }
if ($missing) {
    $rendered = $missing | ForEach-Object { "$($_.Key): $($_.Value)" }
    throw "Cannot synchronize the API 37 platform bundle because source artifacts are missing:`n$($rendered -join "`n")"
}

New-Item -ItemType Directory -Path $ArtifactsDirectory -Force | Out-Null
$lockArtifacts = foreach ($entry in $sources.GetEnumerator()) {
    $target = Join-Path $ArtifactsDirectory $entry.Key
    Copy-Item -LiteralPath $entry.Value -Destination $target -Force
    [ordered]@{
        name = $entry.Key
        sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

[ordered]@{
    schemaVersion = 1
    apiLevel = 37
    buildFingerprint = "Android/sdk_car_mysystemapp_x86_64/emulator_car64_x86_64:Baklava/CP2A.260605.016/eng.binh:userdebug/test-keys"
    artifacts = @($lockArtifacts)
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $LockFile -Encoding utf8

$resolvedArtifactsDirectory = (Resolve-Path -LiteralPath $ArtifactsDirectory).Path
$relativeArtifactsDirectory =
    if ($resolvedArtifactsDirectory.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
        $resolvedArtifactsDirectory.Substring($repoRoot.Length).TrimStart('\', '/')
    } else {
        $resolvedArtifactsDirectory
    }
"platformArtifactsDir=$($relativeArtifactsDirectory.Replace('\', '/'))" |
    Set-Content -LiteralPath (Join-Path $repoRoot "platform-artifacts.properties") -Encoding ascii

[PSCustomObject]@{
    artifactsDirectory = (Resolve-Path -LiteralPath $ArtifactsDirectory).Path
    lockFile = (Resolve-Path -LiteralPath $LockFile).Path
    artifactCount = $lockArtifacts.Count
} | ConvertTo-Json
