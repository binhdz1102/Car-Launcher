# Baseline APK and parity workflow

The local reference APK is `Launcher/apk/CarLauncher.apk`. It is intentionally ignored by Git.
`baseline.lock.json` fixes its identity, platform certificate, target image, and Android user.

Run the static identity check before capturing evidence:

```powershell
.\scripts\verify-baseline.ps1 -VerifyDevice
```

Capture the same state for the reference and an APK under test. `-Install` mutates the connected
AVD; the reference install includes `-d` because it has versionCode 37.

```powershell
.\scripts\capture-parity.ps1 -Label baseline -ApkPath Launcher\apk\CarLauncher.apk -Install -Scenario home -LaunchScenario
.\scripts\capture-parity.ps1 -Label candidate -ApkPath app\build\outputs\apk\release\app-release.apk -Install -Scenario home -LaunchScenario
```

The script stores APK metadata, component resolution, package/activity/window/display dumps, UI XML,
screenshot, and logcat under ignored `artifacts/parity/`. Run both captures from the same restored
AVD snapshot, with the same fixture state and user 10.

Compare the two screenshots after masking clock, date, artwork, and thumbnails that are expected to
change:

```powershell
python scripts\compare-parity.py <baseline-screen.png> <candidate-screen.png> --ignore-rect 0,0,400,120
python scripts\compare-parity-contract.py <baseline-artifact-dir> <candidate-artifact-dir>
```

The default gate requires global RGB SSIM of at least 0.98 and no more than 2% of compared pixels
to exceed a per-channel difference of 16.

The contract comparison rejects any manifest component/permission/intent-filter change and any
change to HOME, App Grid, or QuickStep resolution for user 10. Resource IDs are intentionally
normalized because they are assigned by the candidate build.

`run-parity.ps1` refuses to install either APK until the named AVD snapshot exists. Create and
validate it after the test fixtures have been installed, then run the baseline/candidate matrix:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot
.\scripts\run-parity.ps1 -CandidateApk app\build\outputs\apk\release\app-release.apk
```

The runner restores the snapshot before each label, executes HOME, App Grid, Recents, Calm Mode,
Widget Host, and Map ToS with the same user-10 inputs, compares every pair, restores on failure,
and only leaves a candidate installed after its final HOME smoke passes.

Use `-Scenarios home` for a fast harness smoke; release acceptance always uses the default matrix.
