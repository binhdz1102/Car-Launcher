# Migration matrix

`Launcher/` is reference-only and is never committed. The table maps its
functional areas to the replacement boundaries and the evidence available on
the API 37 AVD.

| Reference area | Replacement module(s) | Main contract/evidence |
| --- | --- | --- |
| `CarLauncher` HOME, map and task cards | `feature:home:*`, `core:platform`, `app` | HOME resolution, clean boot, Maps Placeholder TaskView and reconnect spot checks |
| Media card/source/session | `feature:media:*`, `feature:home:presentation` | fixture media browser, playback/source actions and home state collection |
| Call, projection and assistive cards | `feature:home:*`, `compat` | component/permission contract and empty-card rendering on AVD |
| App Grid, search, shortcuts and reorder | `feature:appgrid:*`, `libraries:appgrid` | App Grid action, search, drag persistence, reset and UXR driving checks |
| ToS / map fallback | `app` (`MapTosActivity`) | explicit activity and Apps action route to App Grid |
| Recents and QuickStep | `feature:recents:*`, `core:platform`, `app` | app-switch, snapshot, open/remove/clear and QuickStep resolution |
| Calm Mode fragment/activity/QC | `feature:calmmode:*`, `app` | provider binding, temperature, translucent window and masked visual SSIM 1.0 |
| Date widget / WidgetHost | `feature:widgets`, `app` | Hilt host creation and Date widget render; latest strict visual gate still differs |
| Dock libraries, events and order | `feature:dock:*`, `libraries:dock*`, `libraries:launcher-common` | protobuf codec, DataStore dual-write and event contracts |
| Car callbacks and SystemUI bridge | `core:platform`, `core:common`, `compat` | callbackFlow adapters, reconnect state and manifest permissions |
| Sample/fixture hosts | `test-apps:fixture` | deterministic media, maps, widget and launcher inputs |

## Compatibility decisions

- Package `com.android.car.carlauncher`, public component names, actions,
  authorities and launcher-owned permissions remain unchanged.
- Resource IDs are not required to be numerically equal; the contract runner
  normalizes build-assigned IDs and compares launcher-owned tokens.
- Stock support-library components merged into the baseline APK are not copied
  when the AVD SystemUI never resolves them; the boundary is documented in the
  contract harness.
- Persistence is dual-written during migration so a rollback can read the
  original order files.

## Remaining parity work

The full run proves manifest/routing compatibility for all six scenarios, but
the strict screenshot gate is not yet green for HOME, App Grid, Recents, Widget
Host and Map ToS. Those surfaces still use simplified XML compositions compared
with the stock DEWD/card/grid styling. This is tracked as visual work, not hidden
by lowering the SSIM threshold.
