# Car Launcher audit test report

Evidence date: 2026-08-14. Baseline is the locked
`Launcher/apk/CarLauncher.apk`; ignored artifact directories contain raw output.

## Verdict

The migration has a buildable platform-signed release and several tested
feature slices. It is **not accepted as a stock-parity replacement**. The
latest AVD attempt stopped during the baseline HOME phase because the launcher
window generated an actual `Input dispatching timed out` ANR. The fail-closed
runner restored the snapshot and did not compare a candidate against invalid
baseline evidence.

## Locked identity

| Item | Value |
| --- | --- |
| Package | `com.android.car.carlauncher` |
| Baseline SHA-256 | `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` |
| Candidate release SHA-256 | `f8a083e01de5e24a40442cf2fb7a99702ad23dde9952718ce7cd71888b864bb8` |
| Certificate | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| Target | API 37 / user 10 / `emulator-5554` |

## Completed gates

- Kotlin compilation, release APK and AndroidTest APK: **PASS**
- AppGrid/Dock order codec round-trip and truncated/corrupt fallback tests:
  **PASS**
- Dock policy and sample-host unit tests: **PASS**
- ktlint for changed modules and release verification: **PASS**
- Overlayable resource contract: **PASS** (354 + 120 items)
- Connected AVD parity acceptance: **NOT RUN TO COMPLETION**

## AVD evidence

The latest run is
`artifacts/parity/audit-run-home/run-20260814-105014/run.json`. Baseline HOME
was marked `INVALID` with reason `Scenario log window contains a fatal, ANR, or
security failure`; logcat records the launcher ANR. The runner also verified
fixture readiness, task topology and the six-test launcher instrumentation
package before stopping. This confirms the guard works; it does not prove
visual or behavioral parity.

The historical run `artifacts/parity/run-20260813-204544` remains useful only
as pre-remediation failure evidence. Its screenshot/contract claims must not be
used as a release gate.

## Release gate still required

Run the full six-scenario matrix three times from a clean snapshot after the
baseline ANR/setup issue is resolved. Every capture must be `PASS`, contract
diffs must be zero outside explicitly allowed version/build fields, all listed
TaskView/UXR/media/Recents/WidgetHost/Dock/data rollback cases must execute,
and screenshots must meet SSIM >= 0.98 with <= 2% different pixels. Leave the
candidate installed only after that final smoke passes.
