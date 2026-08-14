# AVD baseline parity guide

## Locked test input

The official baseline is `Launcher/apk/CarLauncher.apk` with SHA-256
`17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5`. The
target is API 37/user 10 on `emulator-5554`; the recovery snapshot is
`car_launcher_parity_ready`.

## Required procedure

1. Verify baseline, platform artifacts, device boot and fixture APKs.
2. Save and load the snapshot. If save/load fails, stop; do not run a
   destructive suite.
3. Restore the snapshot before every scenario and install baseline with
   `adb install --no-streaming -r -d --user 10`.
4. Restore again, install the platform-signed release candidate, and run the
   identical scenario and instrumentation.
5. Compare screenshots, UI hierarchy, task/display topology and full manifest,
   permission, component, query, routing and overlayable contracts.
6. Restore on any failure. On a pass, reinstall release and run final HOME
   smoke.

The canonical command is:

```powershell
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk `
  -InstrumentationApk app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

The runner covers HOME, App Grid, Recents, Calm Mode, WidgetHost and Map ToS.
Each `capture.json` records `preconditions`, `actions`, `assertions`,
`taskTopology`, `instrumentation` and `logWindow`. `PASS` requires the expected
foreground component, fixture readiness, no duplicate map task, no fatal/ANR/
`SecurityException`, and passing instrumentation. Missing fixture, wrong
foreground or invalid topology is `INVALID` and cannot be compared.

## Screenshot and contract gates

The image comparator uses RGB tolerance 16, SSIM >= 0.98 and <= 2% different
pixels. Only clock/date, artwork and thumbnail regions may be masked; layout,
TaskView and component differences may not be masked. Contract comparison
normalizes line numbers, numeric resource IDs and build version fields only.

The resource lock requires 354 app overlayable items and 120 AppGrid items.
API-compat and resource verification must run before the AVD suite. No report
may call an `INVALID` run a pass. The current audit evidence is
`artifacts/parity/audit-run-home/run-20260814-105014/run.json`; it is invalid
because the baseline HOME log window contains an AVD launcher ANR.
