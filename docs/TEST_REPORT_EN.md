# Car Launcher AVD audit and test report

Verification date: 2026-08-13
Baseline: `Launcher/apk/CarLauncher.apk`
Full parity run: `artifacts/parity/run-20260813-204544` (ignored by Git)

## Verdict

The migration is component- and functionally compatible on the audited API 37
AVD, and the release APK installs as a platform-signed `/data/app` update.
It is **not yet accepted as strict pixel-parity replacement**: the full golden
gate failed for HOME, App Grid, Recents, Widget Host and Map ToS. Calm Mode was
fixed after that run and passed a focused masked comparison. The remaining UI
differences are real styling/layout differences and are not hidden by changing
the gate threshold.

## Environment and identity

| Property | Value |
| --- | --- |
| Serial / user | `emulator-5554` / 10 |
| API / fingerprint | 37 / `Android/sdk_car_mysystemapp_x86_64/emulator_car64_x86_64:Baklava/CP2A.260605.016/eng.binh:userdebug/test-keys` |
| Display | 1920x1080, density 213 |
| Package | `com.android.car.carlauncher` |
| Stock | versionCode 37, `/system/priv-app/CarLauncher` |
| Candidate | versionCode 1000, `custom-dev`, `/data/app` update |
| Baseline SHA-256 | `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` |
| Certificate | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| Candidate release SHA-256 | `6332c187d02a2a0154a2799543c6a6101c3588e76a359308a2a0070baa50d121` |
| Snapshot | `car_launcher_parity_ready` |

## Build and static quality

The following checks completed successfully after the Calm Mode changes:

```text
./gradlew :feature:calmmode:data:ktlintCheck
         :feature:calmmode:presentation:ktlintCheck
         :feature:calmmode:data:detekt
         :feature:calmmode:presentation:detekt
         :app:lintDebug testDebugUnitTest --console=plain --max-workers=2
./gradlew :app:assembleRelease --console=plain --max-workers=2
```

The release certificate is checked by `scripts/verify-release-apk.ps1`; no
credential is printed. The release artifact verified in this handoff is
`6332c187d02a2a0154a2799543c6a6101c3588e76a359308a2a0070baa50d121`.

## Contract parity

`compare-parity-contract.py` returned `passed: true` for all six scenarios in
the full run. The comparison covers the launcher-owned manifest tokens and
HOME, App Grid and QuickStep resolution for user 10.

| Scenario | Contract | Full-run screenshot | SSIM | Different pixels |
| --- | --- | --- | ---: | ---: |
| HOME | Pass | Fail | 0.6913 | 19.3307% |
| App Grid | Pass | Fail | 0.2184 | 11.4891% |
| Recents | Pass | Fail | 0.6913 | 19.3307% |
| Calm Mode (before final fix) | Pass | Fail | 0.3561 | 1.3904% |
| Widget Host | Pass | Fail | 0.4249 | 4.2803% |
| Map ToS | Pass | Fail | 0.2278 | 90.4382% |

The Calm Mode follow-up changed the window to translucent, matched the black
surface and retained the SystemUI control bar. A clean focused capture at
`artifacts/parity/20260813-211333-candidate-calm-mode` compared with the stock
capture using the documented clock/date masks produced `SSIM 1.0` and
`differentPixelRatio 0.0`; its contract comparison also passed.

## Functional AVD evidence

| Area | Result | Evidence |
| --- | --- | --- |
| HOME and map TaskView | Pass spot check | CarService/SystemUI callbackFlow reaches ready; Maps Placeholder surface appears and reconnect path was exercised. |
| Media and home cards | Pass spot check | Fixture media source/session, playback controls and empty card state render. |
| App Grid | Pass functional | discovery, search, reorder persistence, reset-to-A-Z, shortcuts and App Grid action. |
| Driving UXR | Pass | search/reorder are restricted and non-DO tiles are disabled while driving; Park restores them. |
| Recents/QuickStep | Pass functional | app-switch opens `CarRecentsActivity`; snapshot/open/remove/clear paths and QuickStep resolution work. |
| Calm Mode QC/activity | Pass | QC provider opens the translucent activity; date, clock, temperature, media and control bar render. |
| WidgetHost/Date widget | Pass functional | Hilt host binds a valid ID and the Date widget renders. |
| Map ToS | Pass functional | explicit activity opens and Apps routes to App Grid. |
| Dock and persistence | Pass unit/contract | protobuf codec, DataStore dual-write and event contracts pass. |
| Secondary display | Pass with limit | trusted overlay display 2 receives App Grid with the expected display routing; not physical occupant-zone hardware. |

## Stability notes

The latest clean smoke cleared logcat, force-stopped the package and started
Calm Mode; no `FATAL EXCEPTION`, `SecurityException` or launcher process error
was emitted. One earlier full-run capture includes an AVD package-update race
(`ConfigurationController` NPE while the old process was being rebound), not a
screen interaction failure. It was absent after restart, but a release gate
should repeat the full matrix with a fresh restored snapshot.

## Remaining work and acceptance condition

The replacement is suitable for continued functional testing and rollback-safe
development. It must not be labelled pixel-parity complete until HOME media/map
layout, the stock App Grid visual hierarchy, Recents, WidgetHost and Map ToS
match the baseline under the SSIM >= 0.98 / <= 2% gate. A future acceptance run
must also finish with a clean logcat and a release SHA/certificate record.
