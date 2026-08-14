# Car Launcher architecture

This document describes the migrated implementation. `Launcher/` is ignored
reference source. The supported target is the API 37 AVD in `baseline.lock.json`.

## Module boundaries

```text
app
|- platform entry points, manifest components and XML screens
|- core/{common,model,platform,ui,testing}
|- feature/{launcher,home,media,appgrid,recents,calmmode,dock}/{domain,data,presentation}
|- feature/widgets
|- libraries/{appgrid,dock,dock-util,launcher-common}
|- compat
`- test-apps/fixture-app and test-apps/dock-host
```

`domain` owns immutable models and repository contracts. `data` owns Android,
CarService, LauncherApps, media, task, DataStore and protobuf adapters.
`presentation` owns ViewModels, `StateFlow`/`SharedFlow`, activities, fragments
and XML adapters. `app` is the composition root and preserves public package,
component and resource contracts.

The intended dependency rule is `presentation -> domain`, `data -> domain`, and
app composition through Hilt bindings. Domain modules do not import Android.

## State and concurrency

Android callbacks are adapted with `callbackFlow`, repositories expose
`StateFlow`, and UI collection is lifecycle-aware. Shared application scope and
injected dispatchers provide structured concurrency. `Handler` is limited to
Android callback adapters that require a Looper.

```text
CarService/SystemUI/LauncherApps/MediaSession
        -> callbackFlow adapter
        -> repository state and persistence
        -> ViewModel combine/stateIn
        -> XML collection and SharedFlow effects
```

## HOME, TaskView and persistence

`CarLauncher` owns HOME. Controlled TaskView creation waits for the platform
connection, normalizes task callbacks to Flow events, releases on stop or
disconnect and recreates after reconnect. Secondary displays host AppGrid in
the HOME surface instead of creating a second launcher task.

App order dual-writes DataStore and stock `files/order.data`. Dock order
dual-writes DataStore and the stock protobuf; malformed input falls back safely
without deleting the source file.

## Audit boundary

Static gates, resource locks, codec tests and selected feature seams are green.
Full AVD behavior, complete public API dumps and three-run screenshot parity are
still open. See [AUDIT_REMEDIATION_EN.md](AUDIT_REMEDIATION_EN.md).
