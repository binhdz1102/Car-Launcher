[CmdletBinding()]
param(
    [string]$Serial = "emulator-5554",
    [int]$UserId = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$fixturePackage = "com.android.car.carlauncher.fixture"
$mapComponent = "$fixturePackage/.FixtureMapActivity"
$mediaComponent = "$fixturePackage/.FixtureMediaBrowserService"
$mediaPlayAction = "$fixturePackage.action.MEDIA_PLAY"

function Invoke-Adb([string[]]$Arguments) {
    $output = @(& adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

if ((Invoke-Adb @("-s", $Serial, "get-state")) -ne "device") {
    throw "Android device is not ready: $Serial"
}

try {
    $mapStart = Invoke-Adb @("-s", $Serial, "shell", "am", "start", "--user", "$UserId", "-W", "-n", $mapComponent)
    if ($mapStart -notmatch [regex]::Escape($fixturePackage)) {
        throw "Fixture map activity did not start for user $UserId."
    }
    $activityState = Invoke-Adb @("-s", $Serial, "shell", "dumpsys", "activity", "activities")
    if ($activityState -notmatch [regex]::Escape("$fixturePackage/.FixtureMapActivity")) {
        throw "Fixture map task was not reported by ActivityManager."
    }

    Invoke-Adb @(
        "-s", $Serial,
        "shell", "am", "startservice", "--user", "$UserId", "-a", $mediaPlayAction, "-n", $mediaComponent
    ) | Out-Null
    Start-Sleep -Milliseconds 300
    $mediaState = Invoke-Adb @("-s", $Serial, "shell", "dumpsys", "media_session")
    if ($mediaState -notmatch "CarLauncherFixture" -or $mediaState -notmatch "Fixture Drive") {
        throw "Fixture media session was not published."
    }

    [PSCustomObject]@{
        packageName = $fixturePackage
        mapComponent = $mapComponent
        mediaComponent = $mediaComponent
        serial = $Serial
        userId = $UserId
        status = "healthy"
    } | ConvertTo-Json
} finally {
    # A fixture check must not leave a task or playback session behind before taking a snapshot.
    & adb -s $Serial shell am force-stop --user $UserId $fixturePackage | Out-Null
}
