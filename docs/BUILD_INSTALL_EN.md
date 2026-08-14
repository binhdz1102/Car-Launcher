# Build and install guide

## Prerequisites

- Windows PowerShell, JDK 17, Android SDK/build-tools for API 37 and `adb`.
- The matching local AOSP platform jars. `Launcher/` is reference-only and is
  never committed.
- A booted API 37 AVD with user 10 and the locked fingerprint in
  `baseline.lock.json`.

## Sync and verify local artifacts

Platform artifacts are copied to the ignored `platform-artifacts/api-37` tree:

```powershell
.\scripts\sync-platform-artifacts.ps1 `
  -AospOut D:\path\to\aosp\out `
  -PlatformLibrariesDirectory D:\path\to\platform-libs
.\scripts\verify-platform-artifacts.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
```

The commands verify checksums, baseline SHA-256, package/version and the
platform certificate without printing keystore credentials.

## Build and static gates

```powershell
.\gradlew.bat ktlintCheck detekt lintDebug testDebugUnitTest verifyApiCompat `
  :app:assembleDebug :app:assembleRelease :app:assembleAndroidTest `
  --no-daemon --console=plain --max-workers=2
.\scripts\verify-release-apk.ps1 `
  -ApkPath app\build\outputs\apk\release\app-release.apk
```

The release certificate must be
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`.

## Install the candidate

```powershell
adb -s emulator-5554 install --no-streaming -r --user 10 `
  app\build\outputs\apk\release\app-release.apk
adb -s emulator-5554 shell am start --user 10 -W `
  -a android.intent.action.MAIN -c android.intent.category.HOME `
  -n com.android.car.carlauncher/.CarLauncher
```

Verify that `pm path` points to `/data/app/...`; the stock package remains in
`/system/priv-app/CarLauncher`. A different signing key is not acceptable.

## Run baseline parity

Install fixture apps and save the recovery snapshot once:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot -InstallFixtures `
  -FixtureApk test-apps\fixture-app\build\outputs\apk\debug\fixture-app-debug.apk
```

Build the AndroidTest APK, then run the full matrix. The instrumentation APK is
required unless `-SkipInstrumentation` is explicitly used for a non-acceptance
smoke:

```powershell
.\gradlew.bat :app:assembleAndroidTest --no-daemon --console=plain
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk `
  -InstrumentationApk app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

The runner restores `car_launcher_parity_ready` for every label and scenario,
clears all logcat buffers, checks fixtures/foreground/task topology, runs
instrumentation, captures UI hierarchy and screenshots, and restores the
snapshot on `INVALID` or comparison failure. A pass also reinstalls release and
runs final HOME smoke. See [AVD_PARITY_GUIDE_EN.md](AVD_PARITY_GUIDE_EN.md).

## Restore the stock APK

```powershell
.\scripts\install-avd.ps1 -Serial emulator-5554 -UserId 10 -RestoreBaseline
```

The command verifies the locked baseline before installing it with `-r -d`.
For rollback by tag/commit, see [ROLLBACK_EN.md](ROLLBACK_EN.md).
