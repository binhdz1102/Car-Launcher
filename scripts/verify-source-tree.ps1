[CmdletBinding()]
param(
    [string]$ReferenceRoot = "Launcher",
    [string]$LockPath = "contract/source-tree.lock.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ReferenceRoot)) {
    throw "Reference source tree is missing: $ReferenceRoot"
}
if (-not (Test-Path -LiteralPath $LockPath)) {
    throw "Source tree lock is missing: $LockPath"
}

$lock = Get-Content -LiteralPath $LockPath -Raw | ConvertFrom-Json
$excluded = @($lock.excludedDirectories)

function Get-ModuleDigest([string]$modulePath) {
    $root = Join-Path $ReferenceRoot $modulePath
    if (-not (Test-Path -LiteralPath $root)) {
        return [PSCustomObject]@{ exists = $false; fileCount = 0; sha256 = $null }
    }
    $rootFull = (Resolve-Path -LiteralPath $root).Path.TrimEnd('\')
    $rows = @(
        Get-ChildItem -LiteralPath $root -Recurse -File |
            Where-Object {
                $relative = $_.FullName.Substring($rootFull.Length + 1).Replace('\', '/')
                $segments = $relative.Split('/')
                @($segments | Where-Object { $excluded -contains $_ }).Count -eq 0
            } |
            ForEach-Object {
                $relative = $_.FullName.Substring($rootFull.Length + 1).Replace('\', '/')
                $fileHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                "$relative`t$fileHash"
            } |
            Sort-Object
    )
    $canonical = ($rows -join "`n") + "`n"
    $bytes = [Text.Encoding]::UTF8.GetBytes($canonical)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
    return [PSCustomObject]@{ exists = $true; fileCount = $rows.Count; sha256 = $digest }
}

$results = @(
    foreach ($entry in $lock.modules) {
        $actual = Get-ModuleDigest ([string]$entry.path)
        [PSCustomObject]@{
            path = $entry.path
            expectedFileCount = [int]$entry.fileCount
            actualFileCount = $actual.fileCount
            expectedSha256 = $entry.sha256
            actualSha256 = $actual.sha256
            passed = $actual.exists -and
                $actual.fileCount -eq [int]$entry.fileCount -and
                $actual.sha256 -eq [string]$entry.sha256
        }
    }
)

$result = [PSCustomObject]@{
    schemaVersion = $lock.schemaVersion
    referenceRoot = (Resolve-Path -LiteralPath $ReferenceRoot).Path
    lockPath = (Resolve-Path -LiteralPath $LockPath).Path
    modules = $results
    passed = @($results | Where-Object { -not $_.passed }).Count -eq 0
}
$result | ConvertTo-Json -Depth 6
if (-not $result.passed) { exit 1 }
