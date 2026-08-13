# Build and install guide

## Prerequisites

- Windows PowerShell, JDK 17 and the repository Gradle wrapper.
- Android SDK with API 37/build-tools and `adb` on `PATH`.
- A matching AOSP output tree and the local platform libraries. Do not commit
  either the `Launcher/` reference or generated platform artifacts.
- A booted API 37 AVD whose fingerprint and user 10 match `baseline.lock.json`.

## Sync and verify platform artifacts

The source build consumes `android.car.jar`, `framework.jar`, SystemUI shared,
WindowManager Shell and car-qc jars from the matching AOSP output. Synchronize
them into the ignored `platform-artifacts/api-37` directory:

```powershell
.\scripts\sync-platform-artifacts.ps1 `
  -AospOut D:\path\to\out-avd-car-mysystemapp `
  -PlatformLibrariesDirectory D:\path\to\My-System-App\libs\platform
.\scripts\verify-platform-artifacts.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
```

The sync command writes the ignored bundle and `platform-artifacts.lock.json`.
Every checksum must be regenerated from the same AOSP output; never substitute
a jar from another API level. `baseline.lock.json` fixes the original APK SHA-256,
certificate, package, version and AVD fingerprint.

## Build and verify the APK

```powershell
.\gradlew.bat ktlintCheck detekt lintDebug testDebugUnitTest `
  :app:assembleDebug :app:assembleRelease --console=plain --max-workers=2
.\scripts\verify-release-apk.ps1 `
  -ApkPath app\build\outputs\apk\release\app-release.apk
```

The release output must be signed by certificate
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`. The script
prints the artifact SHA-256 without logging any keystore secret.

## Install as the launcher update

Use the checked installer so package path and user are verified:

```powershell
.\scripts\install-avd.ps1 -Serial emulator-5554 -UserId 10 -Build
```

Or install an already-built release explicitly:

```powershell
adb -s emulator-5554 install --no-streaming -r `
  app\build\outputs\apk\release\app-release.apk
adb -s emulator-5554 shell am start --user 10 -W `
  -a android.intent.action.MAIN -c android.intent.category.HOME `
  -n com.android.car.carlauncher/.CarLauncher
```

`pm path` must show `/data/app/...`; the original remains in
`/system/priv-app/CarLauncher`. Release uses the platform certificate, so an
APK signed by a different key must not be installed over the stock package.

## Baseline comparison before acceptance

Install deterministic fixtures, create the named snapshot once, then run the
full baseline/candidate matrix:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot -InstallFixtures `
  -FixtureApk test-apps\fixture\build\outputs\apk\debug\fixture-debug.apk
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk
```

The runner restores `car_launcher_parity_ready` before each label, captures all
six scenarios, writes `run.json` and restores the snapshot on failure. A pass
also reinstalls the release candidate and performs a final HOME smoke. See the
[parity guide](AVD_PARITY_GUIDE_EN.md) for masks and acceptance thresholds.

## Restore the original APK

```powershell
.\scripts\install-avd.ps1 -Serial emulator-5554 -UserId 10 -RestoreBaseline
```

The command verifies the locked baseline first and installs it with `-r -d`.
For a complete rollback procedure, see [ROLLBACK_EN.md](ROLLBACK_EN.md).
