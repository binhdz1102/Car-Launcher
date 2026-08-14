# Source-to-feature and test matrix

`Launcher/` is the AOSP reference and is never committed. A row is marked
`ported`, `equivalent test`, or `open`; an open row is not acceptance evidence.

| AOSP area | Candidate boundary | Current status/evidence |
| --- | --- | --- |
| HOME, map TaskView, passenger display | `feature:home:*`, `app` | Ported lifecycle seam and Flow callbacks; AVD acceptance open |
| Media/session and home cards | `feature:media:*`, `feature:launcher:presentation` | Source/session/card state ported; fixture matrix open |
| Call, projection, assistive/weather | `feature:home:*`, `compat` | Data contracts ported; full UI/priority matrix open |
| App Grid, paging, search, reorder, UXR | `feature:appgrid:*`, `libraries:appgrid` | Paging/UXR/persistence tests pass; full visual/action matrix open |
| ToS, mirroring and shortcuts | `feature:appgrid:*`, `app` | Intent and reducer seams ported; AVD matrix open |
| Recents and QuickStep | `feature:recents:*`, `app` | Horizontal task flow and binder compile; multi-task AVD matrix open |
| Calm Mode QC/activity | `feature:calmmode:*`, `app` | Gate/config/locale/provider tests pass; AVD acceptance open |
| WidgetHost and Date widget | `feature:widgets`, `app` | Rebind and time/locale reducer tests pass; AVD acceptance open |
| Dock library, events and sample host | `libraries:dock*`, `feature:dock:*` | Policy/codec/API seam and host build pass; complete AOSP API open |
| Launcher common/public APIs | `libraries:launcher-common` | Compatibility lock is explicit partial-port |
| Fixture apps and instrumentation | `test-apps:fixture-app`, `app/src/androidTest` | Fixture APK and six contract tests build; full scenario actions open |

## Boundary rules

- Domain modules do not import Android. Application scope and dispatchers are
  injected; `Handler` is limited to mandatory Android callback adapters.
- The launcher app is the composition root. Dock library code is not packaged
  into the launcher when the AOSP host owns Dock.
- App order reads/writes both DataStore and stock `files/order.data`; Dock order
  dual-writes DataStore and the stock protobuf. Malformed input falls back
  without deleting the source file.
- Package, component, action, authority, permission and display-routing changes
  require an explicit contract review. Numeric resource IDs may differ.

## Open acceptance rows

The remaining rows require clean API 37/user 10 AVD runs, full public API dumps,
three-run parity evidence and screenshot thresholds from
[AVD_PARITY_GUIDE_EN.md](AVD_PARITY_GUIDE_EN.md). No row may be changed to
`ported` solely because a unit test or manifest token passes.
