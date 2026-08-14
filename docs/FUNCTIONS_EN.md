# Car Launcher functions

This document describes the current contract and migration boundaries for
`com.android.car.carlauncher`. It is an audited work-in-progress, not a claim
of complete stock parity.

## HOME and media cards

`CarLauncher` preserves HOME/SECONDARY_HOME entry points and uses XML for the
media card and embedded application pane. Media repositories combine
CarMediaManager, MediaSession and MediaController state for title, artwork,
position/duration, playback, seek, source switching and Media Center launch.
Call, projection and assistive card contracts are present; the complete AVD
priority/action matrix remains open.

## Navigation and TaskView

The current-user map intent is resolved before it is sent to a controlled
TaskView. The host reports task appear/info/vanish, bounds, release and
reconnect through Flow. `MapTosActivity` provides the ToS fallback and AppGrid
route. PIP, headless-user, package update and passenger-display cases require
the full parity matrix before acceptance.

## AppGrid and driving safety

LauncherApps discovery, media-service tiles, paging, recent/search/reorder,
reset-to-A-Z, pin/unpin/force-stop/app-info shortcuts and dual persistence are
implemented in the AppGrid feature. `QUERY_ALL_PACKAGES` is retained to match
AOSP and has a local lint suppression with rationale. Car UX restrictions hide
search/reorder while driving and disable non-DO applications.

## Recents, QuickStep, Calm Mode and widgets

Recents uses a horizontal task surface with snapshot/open/remove/clear intents
and the QuickStep binder. Calm Mode has QC/resource gates, locale temperature,
media text and control-bar routing. WidgetHost preserves provider IDs through
stop/start/rebind, and Date widget reacts to time, timezone and locale changes.

## Dock and compatibility

Dock libraries provide item models, ordering policy, event/package receivers,
media/task helpers, an XML host/controller and a sample host. The launcher APK
does not package Dock feature code when the AOSP host owns Dock. The compatibility
lock explicitly labels remaining AOSP public API work as `partial-port`.

## Acceptance

Every row needs unit/equivalent tests plus an AVD action/assertion with no ANR.
Use [MIGRATION_MATRIX_EN.md](MIGRATION_MATRIX_EN.md) and
[AVD_PARITY_GUIDE_EN.md](AVD_PARITY_GUIDE_EN.md).
