# AVD baseline parity guide

## Locked inputs

The official baseline is `Launcher/apk/CarLauncher.apk` with SHA-256
`17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` and the
platform certificate recorded in `baseline.lock.json`. The supported device is
serial `emulator-5554`, API 37, user 10, with snapshot
`car_launcher_parity_ready`.

`Launcher/`, `platform-artifacts/` and `artifacts/` are ignored. The first
command below refuses to run a destructive suite when the snapshot is absent:

```powershell
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\run-parity.ps1 -CandidateApk app\build\outputs\apk\release\app-release.apk
```

## Deterministic procedure

1. Install fixture APKs and create/save the snapshot. If save/load cannot be
   validated, stop; do not run the destructive suite.
2. Restore the snapshot and capture the baseline with `adb install -r -d`.
3. Restore the same snapshot and install the platform-signed candidate release.
4. Run exactly the same six scenarios: HOME, App Grid, Recents, Calm Mode,
   Widget Host and Map ToS.
5. Compare screenshots and launcher-owned manifest/routing artifacts.
6. On any failure restore the snapshot. On a pass reinstall the candidate and
   run the final HOME smoke.

`run-parity.ps1` automates this sequence and writes a run directory containing
`run.json`, one folder per scenario, APK badging, manifest XML tree, dumpsys
activity/window/display, UI hierarchy, screenshot and the last 3000 logcat lines.

## Comparison rules

The contract runner compares launcher-owned components, permissions, actions and
user-10 resolution for HOME, App Grid and QuickStep. Build-assigned resource IDs
and line numbers are normalized. Support-library entries not owned by the
launcher are deliberately excluded and documented in the migration matrix.

The screenshot gate uses RGB pixel tolerance 16, global SSIM >= 0.98 and at most
2% different pixels. Masks may remove dynamic clock/date, artwork, thumbnails or
other explicitly documented non-deterministic regions; do not mask a layout or
component difference. Example Calm Mode comparison:

```powershell
python scripts\compare-parity.py `
  <baseline>\screen.png <candidate>\screen.png `
  --ignore-rect 760,0,420,100 `
  --ignore-rect 560,330,820,400
python scripts\compare-parity-contract.py <baseline> <candidate>
```

## Required behavioral coverage

- clean HOME boot, map TaskView appear/update/remove/reconnect;
- App Grid vertical list, recent/search/reorder, TOS, mirroring and Park/Drive UXR;
- media source, queue/history, call/projection/assistive cards;
- Recents/QuickStep snapshot, open, remove and clear;
- Calm Mode QC, clock/date/temperature/media and translucent control bar;
- WidgetHost and Date widget;
- Dock order/events, secondary display routing and release install/restore.

The latest full run is `artifacts/parity/run-20260813-204544`. Its contract
comparisons passed for all six scenarios; screenshot comparisons failed for
HOME, App Grid, Recents, Widget Host and Map ToS because the replacement UI is
not yet pixel-identical. Do not report that run as an acceptance pass.
