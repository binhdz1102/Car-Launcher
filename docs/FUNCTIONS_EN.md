# Car-Launcher functions

## Home surface

`CarLauncher` is the exported HOME activity with the same package and
component identity as the AOSP launcher. It renders an XML two-pane layout:

- The left pane is the media card.
- The right pane hosts navigation or the selected embedded application.
- The Android Automotive system bars remain outside the app window.

The launcher accepts the standard HOME action and the app-grid return intent.
It is single-task so selecting an app returns to the existing home instance.

## Media

The media repository combines the active car media source and media session
callbacks into `StateFlow` values. The XML card supports:

- source label and source selection popup;
- title, artist, artwork, position, duration, and seek bar;
- previous, play/pause, next, and media-center actions;
- an explicit empty state when no media session is active.

The Android callback APIs use their required main-thread executor only at the
platform boundary; UI state propagation is handled with Flow and lifecycle
collection.

## Navigation and embedded applications

The data layer resolves the vehicle user's `ACTION_MAIN`/`CATEGORY_APP_MAPS`
activity. Application targets are discovered with `LauncherApps`, filtered for
the active user, own package, system-only noise, and Car UX restrictions. A
selected target is represented by a domain state machine:

`Idle -> Loading -> Running`, or `Loading/Running -> Error`.

The presentation layer owns the Android-specific
`ControlledRemoteCarTaskView`. It updates remote window bounds with the XML
container, changes visibility during host interactions, restarts Navigation if
its task vanishes, and reports a bounded timeout when the car service does not
provide a task.

## Application grid

`AppGridActivity` preserves the AOSP app-grid action:
`com.android.car.carlauncher.ACTION_APP_GRID`. It is an XML RecyclerView grid
with four columns and includes:

- a search field;
- a Navigation tile;
- label and icon for each launchable app;
- filtering as the user types;
- return to HOME with either a Navigation-selection extra or an embedded
  component/label extra;
- close and system Back behavior.

The selected app order is persisted with Preferences DataStore. The current
implementation does not use Room because the state is a small ordered list,
not a relational dataset.

## Recents and QuickStep

`CarRecentsActivity` is an XML RecyclerView surface backed by the platform task
manager. It filters out Car-Launcher and SystemUI tasks, displays application
icons and labels, opens a task, removes an individual task, and exposes a
clear-all action. `CarQuickStepService` implements the platform shared
`ILauncherProxy` contract and routes overview requests to this recents surface.

## Architecture and diagnostics

The project follows a small NowInAndroid-inspired multi-module structure:

- domain models and repository interfaces have no Android UI dependency;
- data adapters isolate Car/LauncherApps/media/DataStore APIs;
- ViewModels expose immutable UI state and receive events;
- XML Fragments and Activities render state and bind events;
- Hilt supplies repository and ViewModel dependencies;
- Coroutines and Flow replace polling and application-managed worker threads;
- Timber logs lifecycle, target selection, embedding, media, app-grid, and
  recents failures.

The exact AVD verification and known integration limits are in
`TEST_REPORT_EN.md`.
