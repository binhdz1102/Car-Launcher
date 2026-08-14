# Car Launcher audit test report

Evidence date: 2026-08-14. The official baseline is the ignored
`Launcher/apk/CarLauncher.apk`; raw AVD artifacts are ignored and are referenced
by path below.

## Verdict

The migration now has a platform-signed release, truthful static gates, a
fail-closed parity runner, and valid HOME/App Grid captures. It is **not
accepted as a stock-parity replacement**: the candidate HOME UI is still not
the AOSP media/home-card layout, the merged resource set is incomplete, and
Recents baseline setup still returns to Maps with fatal errors on this AVD.

## Locked identity

| Item | Value |
| --- | --- |
| Package | `com.android.car.carlauncher` |
| Baseline SHA-256 | `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` |
| Candidate release SHA-256 | `2bd26ef1b499e70c8f49a9a6b04b089c190dd36ea6cc75f9c3d07ee0419dc335` |
| Platform certificate | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| Target | API 37/Baklava, user 10, `emulator-5554` |

## Completed gates

- `ktlintCheck`, `detekt`, targeted `lintDebug`, architecture/source/resource
  checks, all `testDebugUnitTest` suites, debug/release and AndroidTest APK
  builds: **PASS**.
- AppGrid/Dock order codec, corrupt/truncated fallback, media-controller
  selection and fixture contract tests: **PASS**.
- Overlayable source contract: **PASS** (354 app + 120 AppGrid items).
- Release APK certificate verification: **PASS** (SHA-256 above).
- Full parity acceptance: **FAIL / OPEN**; no release claim is made.

## AVD evidence

1. `artifacts/parity/controlled-home-mediafix-20260814/run-20260814-161355`
   - baseline and candidate capture metadata: `PASS`;
   - fixture media is selected by the candidate (`Fixture Drive`), and both
     task topology/log windows are valid;
   - screenshot: SSIM `0.8538034994`, different pixels `8.7308578%` — **FAIL**;
   - contract: **FAIL** (candidate `application` name/`testOnly` attributes and
     3,884 baseline-only / 199 candidate-only resource tokens).
2. `artifacts/parity/remediation-matrix-20260814b/run-20260814-162034`
   - baseline HOME and App Grid captures passed;
   - baseline Recents was correctly returned as `INVALID`: Maps became
     top-resumed and logcat contained two fatal exceptions. The runner stopped
     before comparing a candidate, as required.
3. `artifacts/parity/recents-direct2-20260814/20260814-163744-baseline-recents`
   - deterministic utility/media/map fixture tasks were seeded and recorded;
   - stock Recents still ended on Maps with fatal errors, therefore this is
     invalid baseline evidence, not a candidate pass.

The historical `artifacts/parity/run-20260813-204544` remains pre-remediation
failure evidence only.

## Open acceptance conditions

- Port the remaining AOSP media/home-card, App Grid, Recents/QuickStep,
  Calm/WidgetHost, Dock and public-library implementations and map every public
  symbol to an equivalent test or an approved N/A reason.
- Match the merged manifest/resource contract (including transitive Car UI and
  SystemUI resources) and remove unexpected application attributes.
- Make the baseline Recents fixture/setup stable, then run the complete matrix
  three consecutive times from `car_launcher_parity_ready`, with instrumentation.
- Require `PASS` captures, no ANR/fatal/SecurityException, topology equivalence,
  SSIM >= 0.98 and <= 2% different pixels before leaving the candidate installed.
