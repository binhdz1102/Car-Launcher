# Baseline APK and parity workflow

The local reference is `Launcher/apk/CarLauncher.apk` and is ignored by Git.
`baseline.lock.json` fixes its SHA-256, certificate, package/version, target
image and user.

Verify the inputs first:

```powershell
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\verify-platform-artifacts.ps1 -VerifyDevice -Serial emulator-5554
```

The full runner requires the deterministic fixture and AndroidTest APK:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot -InstallFixtures `
  -FixtureApk test-apps\fixture-app\build\outputs\apk\debug\fixture-app-debug.apk
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk `
  -InstrumentationApk app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

The runner restores `car_launcher_parity_ready` before every label/scenario,
installs baseline with downgrade allowed, installs candidate release, clears all
logcat buffers, checks fixture/foreground/task topology, runs instrumentation,
captures UI XML/screenshots and restores the snapshot on failure. A capture
without a valid precondition is `INVALID`, never a pass.

The screenshot gate is RGB tolerance 16, SSIM >= 0.98 and <= 2% different
pixels. Only documented clock/date/artwork/thumbnail masks are allowed. The
contract gate compares manifest components, permissions, intent filters,
metadata, queries, routes and resource names; only build version fields,
numeric resource IDs and line numbers are normalized.

For a non-acceptance harness smoke, use `-Scenarios home -SkipInstrumentation`.
The latest HOME evidence is recorded at
`artifacts/parity/controlled-home-mediafix-20260814/run-20260814-161355`:
captures are valid, but screenshot and manifest/resource comparison still fail.
The latest deterministic Recents setup is
`artifacts/parity/recents-direct2-20260814/20260814-163744-baseline-recents`;
it is `INVALID` because stock Maps remains top-resumed with fatal logcat errors.
