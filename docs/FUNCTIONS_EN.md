# Car-Launcher functions

This document describes the replacement implementation of
com.android.car.carlauncher on the audited Automotive AVD. Public launcher
components remain compatible with the stock package while the internals use a
modern multi-module architecture.

## HOME and Car service lifecycle

CarLauncher handles MAIN, HOME, SECONDARY_HOME, DEFAULT and LAUNCHER_APP. It
retains the system launcher's singleTask, stateNotNeeded, resumeWhilePausing and
ALLOW_HOME_ACTIVITY_ALWAYS_PRESENT contracts.

HOME contains a media card on the left and an embedded-application pane on the
right. Every surface applies system-bar insets. The shared CarServiceConnection
does not block the main thread, publishes state with StateFlow and reconnects
when the Car service dies or becomes ready again.

## Media

MediaRepository combines CarMediaManager, MediaSessionManager and
MediaController callbacks. The UI supports:

- reconnection to the current source and session;
- title, artist, artwork, position and duration;
- previous, play/pause, next and seek;
- media-source discovery and switching;
- Media Center launch for the selected source;
- a bounded empty state when metadata or a session is unavailable.

Calm Mode uses the same repository, so its playback state and controls remain
consistent with HOME.

## Navigation and TaskView

The default map application is resolved through MAIN/CATEGORY_APP_MAPS for the
current user. ControlledRemoteCarTaskView embeds it in the right pane, updates
bounds from the XML container, preserves the selected target across HOME
lifecycle changes and recovers after a vanished task.

A state machine owns Idle, Loading, Running and Error. Requests have a finite
timeout; when CarSystemUIProxy or the Car service is unavailable, the UI exposes
a retryable error instead of hanging or crashing. On a clean boot of the target
image, Maps Placeholder creates a real SurfaceView/task and delivers
onTaskAppeared.

MapTosActivity keeps the fallback component required by scalable SystemUI and
offers an App Grid action until a map is configured.

## App Grid, driving safety and reset

App Grid is available through
com.android.car.carlauncher.ACTION_APP_GRID and the stock explicit component
com.android.car.carlauncher/.AppGridActivity. Its activity uses singleInstance
like the stock APK.

LauncherApps supplies current-user activities, while declared
MediaBrowserService queries supply media tiles. QUERY_ALL_PACKAGES is not
requested. The RecyclerView provides:

- a Navigation tile;
- icons, labels and activity/media launch;
- live search;
- parked-only drag ordering;
- Preferences DataStore persistence;
- package add/remove/change observation;
- DiffUtil updates.

CarUxRestrictions are checked both while rendering and immediately before
launch. In the injected driving state, search is hidden, drag ordering is
disabled and non-distraction-optimized applications are disabled; media and DO
applications remain available. A missing safety service causes a fail-safe
disabled state.

ResetLauncherActivity is published under Apps in Automotive Settings. After
confirmation it clears DataStore ordering and the grid immediately returns to
A-Z.

## Recents and QuickStep

CarQuickStepService implements the image's SystemUI ILauncherProxy shared
contract. Overview or KEYCODE_APP_SWITCH opens CarRecentsActivity; requesting
overview again returns to the top running task.

RecentTasksRepository uses WindowManager Shell IRecentTasks with an
ActivityManager fallback. It:

- selects tasks for the current display;
- excludes the launcher, SystemUI and permission controller;
- loads labels, icons and task snapshots;
- opens tasks with startActivityFromRecents;
- removes one task or clears all tasks;
- publishes state with StateFlow.

READ_FRAME_BUFFER is requested and granted, so thumbnails are real task
snapshots rather than placeholders.

## Image compatibility components

In addition to HOME, App Grid and Recents, the APK publishes all launcher-owned
components present in the stock manifest:

- ControlBarActivity and WidgetHostActivity host AppWidgets, show the configured
  Date widget and retain widget IDs;
- DateAppWidgetProvider updates on date, time and timezone changes;
- CalmModeQCProvider at
  content://com.android.car.carlauncher.calmmode/calm_mode allows SystemUI to
  bind the Quick Control;
- CalmModeActivity presents clock and media controls on a low-distraction screen;
- MapTosActivity is an embeddable map fallback;
- InCallServiceImpl retains the Telecom binding while call UI remains owned by
  CarSystemUI, as in the stock package;
- ResetLauncherActivity provides the Automotive Settings App Grid reset entry.

Critical privileged permissions, including MANAGE_ACTIVITY_TASKS,
START_TASKS_FROM_RECENTS, READ_FRAME_BUFFER and BIND_APPWIDGET, are verified as
granted after the update installation.

## Architecture

- domain owns platform-independent models, repository contracts and state;
- data isolates Car APIs, LauncherApps, media, WindowManager Shell and DataStore;
- presentation uses ViewModels, immutable state and lifecycle-aware Flow;
- app assembles platform entry points and compatibility contracts;
- Hilt supplies dependencies, while Coroutines/Flow replace polling and
  manually-managed workers;
- Java/Kotlin 17, minSdk 34, compileSdk 36 and targetSdk 36.

Room is unnecessary for one small ordered list. An AndroidX navigation graph is
also unnecessary because the platform enters through fixed activities/actions.

## Verified boundary

This build provides the functional parity needed to replace the launcher on the
audited API 37 AVD. It does not claim pixel parity with every DEWD/OEM overlay.
The AVD has one real occupant display; a trusted 1280x720 overlay display
verified layout/routing but cannot replace a passenger occupant-zone test on
multi-display hardware. The image also lacks a real Telecom call and a
metadata-rich media provider, so those contracts were verified through
bind/resolve behavior and the sessions available on the AVD.
