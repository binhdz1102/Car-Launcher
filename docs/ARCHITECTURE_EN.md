# Car Launcher architecture

This document describes the migrated implementation, not the ignored `Launcher/`
reference tree. The supported target is the API 37 AVD in `baseline.lock.json`.

## Module boundaries

```text
app
├─ platform entry points, manifest components and XML screens
├─ core/{common,model,platform,ui,testing}
├─ feature/{launcher,home,media,appgrid,recents,calmmode,dock}/{domain,data,presentation}
├─ feature/widgets
├─ libraries/{appgrid,dock,dock-util,launcher-common}
├─ compat
└─ test-apps/fixture
```

`domain` contains immutable models and repository contracts. `data` owns Android,
CarService, LauncherApps, MediaSession, TaskManager, DataStore and protobuf
adapters. `presentation` owns ViewModels, `StateFlow`/`SharedFlow`, activities,
fragments and XML/ViewBinding adapters. `app` is the composition root and keeps
the public package/component/resource contract.

The dependency rule is `presentation -> domain`, `data -> domain`, and
`app -> presentation/data`. A feature never reaches into another feature's
concrete data adapter. Hilt modules bind platform implementations at the app
boundary.

## State and concurrency

Android callbacks are converted at the edge with `callbackFlow`, then exposed as
`StateFlow` from repositories and ViewModels. UI collection is lifecycle-aware
(`repeatOnLifecycle`), and work is cancelled with structured concurrency. A
`Handler` is permitted only inside an adapter when an Android callback API
requires a `Looper`; it is not used as application state orchestration.

The normal path is:

```text
CarService / SystemUI / LauncherApps / MediaSession
        -> callbackFlow adapter
        -> repository state + persistence
        -> ViewModel.combine/stateIn
        -> XML view collection and one-shot SharedFlow effects
```

## HOME and TaskView

`CarLauncher` owns the HOME window. `ControlledRemoteCarTaskView` is created only
after the `CarSystemUIProxy`/CarService connection reports ready. The lifecycle
is deterministic: create, register task callback, initialize with the display
and launch options, accept `onTaskAppeared`, then remove/update/reconnect on
task callbacks. On `onStop` or service loss the controller unregisters and
releases the view; a later ready signal creates a fresh controller. Maps
Placeholder is the deterministic fixture used by the AVD evidence.

The home repository combines media, map/task state, call/projection/assistive
card models and dock order. A media/task update is reduced to one UI state so a
reconnect cannot leave a stale SurfaceView or stale card visible.

## App Grid and UXR

`LauncherApps` discovery and media-service tiles are normalized into app-grid
domain models. DataStore stores drag order; the stock `files/order.data` format
is read during migration and dual-written while rollback is possible. Search,
reset-to-A-Z, shortcuts and launch are ViewModel intents. The UXR repository
publishes a `StateFlow` for Park/Drive restrictions; presentation derives
disabled tiles and removes search/reorder affordances while driving.

## Recents and QuickStep

The recents data adapter reads task snapshots and task metadata through the
platform APIs. `CarRecentsActivity` exposes open/remove/clear intents via a
ViewModel. `CarQuickStepService` remains the SystemUI binder entry point and
routes overview requests to the same recents state. Snapshot access is guarded
by the privileged permission retained in the manifest.

## Calm Mode and widgets

Calm Mode uses a translucent activity so the SystemUI control bar remains above
the black surface, matching stock window routing. Clock/date are XML `TextClock`
views; temperature is a CarProperty callbackFlow with C/F conversion; media is
combined in `CalmModeViewModel`. WidgetHost uses `AppWidgetHost` with a stable
host ID and persists provider bindings per display. The Date widget and empty
state are deterministic fixtures.

## Display and persistence compatibility

Every surface derives the current `DisplayTarget` and applies insets through the
shared UI helper. Dock order is dual-written to DataStore and the stock
protobuf-compatible file. Launcher order follows the same migration rule. This
allows a rollback to the stock APK without losing app or dock order.

The platform keystore, API 37 artifacts, baseline APK identity and AVD
fingerprint are locked outside source code. Build verification checks the
artifact checksums and release certificate before installation.
