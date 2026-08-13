[CmdletBinding()]
param(
    [string]$CandidateApk,
    [string]$BaselineApk,
    [string]$Serial = "emulator-5554",
    [int]$UserId = 10,
    [string]$SnapshotName = "car_launcher_parity_ready",
    [string]$ArtifactsRoot,
    [string]$FixtureApk,
    [string[]]$IgnoreRect = @("0,0,1920,76"),
    [ValidateSet("home", "app-grid", "recents", "calm-mode", "widget-host", "map-tos")]
    [string[]]$Scenarios = @("home", "app-grid", "recents", "calm-mode", "widget-host", "map-tos"),
    [switch]$CreateSnapshot,
    [switch]$InstallFixtures
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($BaselineApk)) {
    $BaselineApk = Join-Path $repoRoot "Launcher\apk\CarLauncher.apk"
}
if ([string]::IsNullOrWhiteSpace($ArtifactsRoot)) {
    $ArtifactsRoot = Join-Path $repoRoot "artifacts\parity"
}

function Invoke-AdbText([string[]]$Arguments) {
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
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [PSCustomObject]@{
        ExitCode = $process.ExitCode
        StandardOutput = $stdout
        StandardError = $stderr
    }
}

function Wait-ForDevice {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        $stateResult = Invoke-AdbText @("-s", $Serial, "get-state")
        $bootResult = Invoke-AdbText @("-s", $Serial, "shell", "getprop", "sys.boot_completed")
        $state = $stateResult.StandardOutput.Trim()
        $bootCompleted = $bootResult.StandardOutput.Trim()
        if ($state -eq "device" -and $bootCompleted -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "AVD did not become ready after snapshot operation: $Serial"
}

function Get-SnapshotList {
    $snapshotResult = Invoke-AdbText @("-s", $Serial, "emu", "avd", "snapshot", "list")
    if ($snapshotResult.ExitCode -ne 0) {
        throw "Unable to query AVD snapshots: $($snapshotResult.StandardError)"
    }
    return $snapshotResult.StandardOutput
}

function Test-SnapshotExists {
    $snapshots = Get-SnapshotList
    return $snapshots -match [regex]::Escape($SnapshotName)
}

function Assert-SnapshotExists {
    if (-not (Test-SnapshotExists)) {
        throw "Snapshot '$SnapshotName' is unavailable. Refusing to run a destructive parity suite. Run with -CreateSnapshot after fixtures are installed."
    }
}

function Save-Snapshot {
    $saveResult = Invoke-AdbText @("-s", $Serial, "emu", "avd", "snapshot", "save", $SnapshotName)
    if ($saveResult.ExitCode -ne 0) {
        throw "Failed to save AVD snapshot '$SnapshotName': $($saveResult.StandardError)"
    }
    Assert-SnapshotExists
}

function Remove-Snapshot {
    $removeResult = Invoke-AdbText @("-s", $Serial, "emu", "avd", "snapshot", "delete", $SnapshotName)
    if ($removeResult.ExitCode -ne 0) {
        throw "Failed to remove AVD snapshot '$SnapshotName': $($removeResult.StandardError)"
    }
}

function Restore-Snapshot {
    $loadResult = Invoke-AdbText @("-s", $Serial, "emu", "avd", "snapshot", "load", $SnapshotName)
    if ($loadResult.ExitCode -ne 0) {
        throw "Failed to restore AVD snapshot '$SnapshotName': $($loadResult.StandardError)"
    }
    Wait-ForDevice
}

function Capture-Scenario([string]$Label, [string]$ApkPath, [string]$Scenario, [bool]$InstallApk) {
    $captureScript = Join-Path $scriptRoot "capture-parity.ps1"
    $captureParameters = @{
        Label = $Label
        ApkPath = $ApkPath
        Serial = $Serial
        UserId = $UserId
        ArtifactsRoot = $ArtifactsRoot
        Scenario = $Scenario
        LaunchScenario = $true
    }
    if ($InstallApk) {
        $captureParameters.Install = $true
    }
    $output = @(& $captureScript @captureParameters)
    if (-not $?) {
        throw "Capture failed for $Label/$Scenario."
    }
    return [string]$output[$output.Length - 1]
}

& (Join-Path $scriptRoot "verify-baseline.ps1") -ApkPath $BaselineApk -VerifyDevice -Serial $Serial | Out-Null
Wait-ForDevice
if ($CreateSnapshot) {
    if ($InstallFixtures) {
        $fixtureParameters = @{
            Serial = $Serial
            UserId = $UserId
        }
        if (-not [string]::IsNullOrWhiteSpace($FixtureApk)) {
            $fixtureParameters.ApkPath = $FixtureApk
        }
        & (Join-Path $scriptRoot "install-fixtures.ps1") @fixtureParameters | Out-Null
        & (Join-Path $scriptRoot "verify-fixtures.ps1") -Serial $Serial -UserId $UserId | Out-Null
    }
    # Installing a fixture changes the deterministic test input. Replace an existing snapshot
    # only on this explicit setup path so normal parity runs never mutate recovery state.
    $created = -not (Test-SnapshotExists)
    if ($InstallFixtures -and -not $created) {
        Remove-Snapshot
        $created = $true
    }
    if ($created) {
        Save-Snapshot
    }
    Restore-Snapshot
    [PSCustomObject]@{
        snapshotName = $SnapshotName
        serial = $Serial
        userId = $UserId
        fixturesInstalled = [bool]$InstallFixtures
        status = if ($created) { "created-and-restored" } else { "existing-and-restored" }
    } | ConvertTo-Json
    exit 0
}
if ([string]::IsNullOrWhiteSpace($CandidateApk)) {
    throw "CandidateApk is required unless -CreateSnapshot is supplied."
}
if (-not (Test-Path -LiteralPath $CandidateApk)) {
    throw "Candidate APK is missing: $CandidateApk"
}
Assert-SnapshotExists

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $ArtifactsRoot "run-$timestamp"
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$scenarios = $Scenarios
$baselineArtifacts = [ordered]@{}
$candidateArtifacts = [ordered]@{}
$comparisonResults = @()
$completed = $false
$failureMessage = $null

function Write-RunArtifact {
    param(
        [Parameter(Mandatory)]
        [string]$Status,
        [string]$ErrorMessage
    )

    [PSCustomObject]@{
        snapshotName = $SnapshotName
        serial = $Serial
        userId = $UserId
        status = $Status
        baselineArtifacts = $baselineArtifacts
        candidateArtifacts = $candidateArtifacts
        comparisons = $comparisonResults
        finalSmokeArtifact = if ($completed) { $finalSmoke } else { $null }
        passed = $completed
        error = $ErrorMessage
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $runDir "run.json") -Encoding utf8
}

try {
    Restore-Snapshot
    $install = $true
    foreach ($scenario in $scenarios) {
        $baselineArtifacts[$scenario] = Capture-Scenario "baseline" $BaselineApk $scenario $install
        $install = $false
    }

    Restore-Snapshot
    $install = $true
    foreach ($scenario in $scenarios) {
        $candidateArtifacts[$scenario] = Capture-Scenario "candidate" $CandidateApk $scenario $install
        $install = $false
    }

    foreach ($scenario in $scenarios) {
        $scenarioDir = Join-Path $runDir $scenario
        New-Item -ItemType Directory -Path $scenarioDir -Force | Out-Null
        $imageArguments = @(
            (Join-Path $baselineArtifacts[$scenario] "screen.png"),
            (Join-Path $candidateArtifacts[$scenario] "screen.png"),
            "--output", (Join-Path $scenarioDir "screenshot.json")
        )
        foreach ($rect in $IgnoreRect) {
            $imageArguments += @("--ignore-rect", $rect)
        }
        & python (Join-Path $scriptRoot "compare-parity.py") @imageArguments | Out-Null
        $imagePassed = $LASTEXITCODE -eq 0
        & python (Join-Path $scriptRoot "compare-parity-contract.py") $baselineArtifacts[$scenario] $candidateArtifacts[$scenario] `
            "--output" (Join-Path $scenarioDir "contract.json") | Out-Null
        $contractPassed = $LASTEXITCODE -eq 0
        $comparisonResults += [PSCustomObject]@{
            scenario = $scenario
            screenshotPassed = $imagePassed
            contractPassed = $contractPassed
        }
    }
    if ($comparisonResults | Where-Object { -not $_.screenshotPassed -or -not $_.contractPassed }) {
        throw "Baseline comparison failed. The AVD will be restored to '$SnapshotName'."
    }

    # A passing result is only accepted after reinstalling the release candidate and rerunning HOME.
    $finalSmoke = Capture-Scenario "candidate" $CandidateApk "home" $true
    $completed = $true
    Write-RunArtifact -Status "passed"
    Write-Output (Resolve-Path -LiteralPath $runDir).Path
} catch {
    $failureMessage = $_.Exception.Message
    throw
} finally {
    if (-not $completed) {
        try {
            Restore-Snapshot
        } finally {
            Write-RunArtifact -Status "failed" -ErrorMessage $failureMessage
        }
    }
}
