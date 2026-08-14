# Car Launcher audit remediation status

This document is the release-facing summary of the AOSP migration. `Launcher/`
and generated parity/platform artifacts are reference-only and remain ignored.

## Locked inputs

- Baseline: `Launcher/apk/CarLauncher.apk`
- Baseline SHA-256: `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5`
- Platform certificate SHA-256: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`
- Target: API 37 AVD, user 10, serial `emulator-5554`
- Recovery snapshot: `car_launcher_parity_ready`

## Remediation commits

The rollback chain starts at tag `audit-remediation-start-20260814` and is
kept on `main`:

1. `bf18a04` quality gates and truthful lint suppressions
2. `554889b` fail-closed parity capture and snapshot runner
3. `4ec7636` merged manifest/resource and 474 overlayable contract
4. `25da7c9` AppGrid, Dock and common API seams
5. `99b8b11` injectable flags, application scope and callback Flow boundaries
6. `0f7e9fb` HOME/TaskView lifecycle and secondary-display host
7. `3d4b3c5` media, call, projection and assistive card state
8. `6de6366` App Grid paging, UXR, shortcuts and persistence
9. `e64c3e8` Recents/QuickStep task flow
10. `9425eec` Calm Mode, WidgetHost and Date widget lifecycle
11. `d2a9800` Dock library behavior and sample host
12. `4085451` rollback-safe order codecs and full parity harness
13. `1ba8712` final static-gate fixes, platform permission lint contracts and media cleanup
14. `8d31380` clean module-boundary checks, source lock and atomic persistence
15. `d3278b9` padded AppGrid pages and AOSP resource/accessibility hierarchy
16. `736de9c` fail-closed UI/task preconditions, fixture seeding and semantic contract diff
17. `7cdea62` main-thread TaskView callback lifecycle and media-session handoff scoring
18. `b14eca0` platform manifest compatibility endpoints and API 37 release configuration

The library/API locks intentionally report `partial-port` where the complete
AOSP implementation is not yet present. This prevents a passing seam check
from being mistaken for full behavioral parity.

## Evidence status

Static verification completed after the remediation slices:

- Full Gradle static/unit/build gate (including `verifyArchitecture` and
  `:app:assembleAndroidTest`): **PASS**
- AppGrid/Dock/media/data unit tests and ktlint/detekt: **PASS**
- Resource contract: **PASS** (354 app + 120 AppGrid overlayable items)
- Source-tree and boundary checks: **PASS**; AppGrid/Dock API status remains
  `partial-port` and is therefore not an acceptance pass.
- Release APK verification: **PASS**; SHA-256
  `2bd26ef1b499e70c8f49a9a6b04b089c190dd36ea6cc75f9c3d07ee0419dc335`
- Full AVD parity acceptance: **NOT ACCEPTED**

The latest valid HOME run is
`artifacts/parity/controlled-home-mediafix-20260814/run-20260814-161355`:
both captures and task/log topology are `PASS`, but screenshot SSIM is
`0.8538034994` with `8.7308578%` different pixels and the manifest/resource
contract fails. The candidate now selects the seeded Fixture Drive session;
the remaining difference is the HOME media/card layout, not a missing fixture.

The Recents run
`artifacts/parity/recents-direct2-20260814/20260814-163744-baseline-recents`
records deterministic fixture task creation, then correctly returns `INVALID`
because stock Recents exposes Maps as top-resumed and logcat contains two fatal
exceptions. `artifacts/parity/remediation-matrix-20260814b/run-20260814-162034`
stopped at the same fail-closed baseline precondition. These are invalid setup
runs, not parity passes. `artifacts/parity/run-20260813-204544` remains
historical failure evidence only.

## Acceptance conditions still open

Before replacing the stock launcher, all of the following must be demonstrated
three consecutive times from a clean snapshot:

1. Baseline and candidate captures are `PASS`, with no ANR, fatal exception or
   `SecurityException`; missing fixture, wrong foreground or wrong task/display
   topology is `INVALID`.
2. Merged manifest, queries, permissions, components, intent filters,
   metadata, routing and resource/overlayable contracts have zero unexpected
   diffs.
3. AppGrid, Dock and launcher-common API dumps are mapped to the AOSP public
   symbols; remaining partial symbols have an explicit port or N/A reason.
4. HOME/TaskView, media/call/projection, App Grid, Recents/QuickStep,
   Calm/WidgetHost, Dock, secondary display and both-way data rollback tests
   pass on API 37/user 10.
5. Each screenshot reaches SSIM >= 0.98 and <= 2% different pixels after only
   documented clock/date/artwork/thumbnail masks; no layout or TaskView region
   may be masked.

Use [BUILD_INSTALL_EN.md](BUILD_INSTALL_EN.md),
[AVD_PARITY_GUIDE_EN.md](AVD_PARITY_GUIDE_EN.md) and
[ROLLBACK_EN.md](ROLLBACK_EN.md) for the reproducible commands.
