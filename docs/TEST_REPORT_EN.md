# Car-Launcher AVD audit and test report

Final verification date: 2026-08-08

## Verdict

**Ready to replace the default launcher on the audited Automotive AVD.**

The custom APK retains the stock package, platform signature, privileged
permissions and all launcher-owned component contracts. HOME, App Grid,
TaskView, Media, Recents/QuickStep, Calm Mode QC, WidgetHost, map fallback and
App Grid reset were exercised directly on the emulator. The final build is
active as a system-app update under /data/app; the original /system APK remains
as fallback.

This verdict does not automatically cover another image/OEM with a different
certificate, platform JAR, overlay or component contract.

## Environment

| Property | Value |
| --- | --- |
| Serial | emulator-5554 |
| AVD | my_car_avd_mysystemapp_20260802 |
| Fingerprint | Android/sdk_car_mysystemapp_x86_64/emulator_car64_x86_64:Baklava/CP2A.260605.016/eng.binh:userdebug/test-keys |
| OS | Android 17, API 37, userdebug |
| User | Automotive user 10 |
| Main display | 1920x1080, density 213 |
| Package | com.android.car.carlauncher |
| Stock | versionCode 37, versionName Baklava, /system/priv-app/CarLauncher |
| Custom | versionCode 1000, versionName custom-dev, minSdk 34, targetSdk 36 |
| Final vehicle state | Parked, baseline UXR |

Platform certificate SHA-256 for both debug and release:

~~~text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
~~~

## APK and contract audit

The binary manifest of /system/priv-app/CarLauncher/CarLauncher.apk was compared
with the custom APK. Every launcher-owned component has a corresponding
implementation:

- CarLauncher;
- AppGridActivity and ACTION_APP_GRID;
- ResetLauncherActivity;
- CarRecentsActivity and CarQuickStepService;
- ControlBarActivity and WidgetHostActivity;
- CalmModeActivity and CalmModeQCProvider;
- MapTosActivity;
- InCallServiceImpl;
- DateAppWidgetProvider.

Stock support-library components merged from Car UI or WindowManager Shell,
such as CarUiInstaller, SearchResultsProvider and desktop plumbing, are not
launcher contracts invoked by this image's SystemUI. The custom app does not use
that Car UI stack and implements its XML surfaces directly.

The image's SystemUI/RRO was also inspected for actual references: the explicit
App Grid component, QuickStep service, CarRecentsActivity and
content://com.android.car.carlauncher.calmmode/calm_mode.

## Build and automated tests

Final quality gate:

~~~text
bash ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest   :app:assembleDebug :app:assembleRelease --console=plain --max-workers=2

BUILD SUCCESSFUL
586 actionable tasks
~~~

AVD instrumentation:

~~~text
bash ./gradlew :app:connectedDebugAndroidTest --console=plain --max-workers=2

Starting 6 tests on my_car_avd_mysystemapp_20260802(AVD) - 17
Finished 6 tests on my_car_avd_mysystemapp_20260802(AVD) - 17
BUILD SUCCESSFUL
~~~

The six tests cover package identity, critical granted permissions, HOME/App
Grid, distraction-optimized activities, QuickStep/InCall services, the Calm
Mode provider, Date widget and ResetLauncher Settings entry.

## Overwrite installation

The retained source-controlled installation script completed successfully:

~~~text
bash scripts/install-avd.sh emulator-5554
Performing Streamed Install
Success
Active replacement: package:/data/app/.../base.apk (versionCode=1000)
~~~

It uses adb install -r without a downgrade flag, checks the device, active path
and versionCode, then starts HOME. connectedDebugAndroidTest removes the target
APK after testing, so the script must be run afterward; that final reinstall was
completed.

Dumpsys confirms these permissions are granted to the update:

- android.permission.MANAGE_ACTIVITY_TASKS;
- android.permission.START_TASKS_FROM_RECENTS;
- android.permission.READ_FRAME_BUFFER;
- android.permission.BIND_APPWIDGET.

## Direct verification matrix

| Area | Result | Observed evidence |
| --- | --- | --- |
| HOME resolution | Pass | HOME resolves to com.android.car.carlauncher/.CarLauncher; versionCode 1000 runs from /data/app. |
| HOME layout/insets | Pass | Media and embedded panes fit the 1920x1080 safe area without status/navigation-bar overlap. |
| Car service | Pass | State becomes ready=true after launch without blocking the UI. |
| Media | Pass | Radio source/session is selected; empty state, play/pause, previous/next, source list and Media Center follow session capabilities. |
| TaskView | Pass | CarTaskViewController connects; ControlledRemoteCarTaskView creates a SurfaceView, initializes and receives onTaskAppeared for Maps Placeholder. |
| SystemUI App Grid | Pass | Tapping the system-bar grid button opens the custom AppGridActivity. |
| App discovery | Pass | LauncherApps and media tiles remain populated after removing QUERY_ALL_PACKAGES; Settings/media launches work. |
| Search | Pass | Label search filters the list; the keyboard path is only exposed when UXR allows it. |
| Reorder/persistence | Pass | Dragging AdasLocationTestApp changes order and survives closing/reopening the grid. |
| App Grid reset | Pass | ResetLauncherActivity shows confirmation; OK clears DataStore and immediately restores A-Z order. |
| Driving UXR | Pass | Injected driving state hides search, disables non-DO apps, keeps media enabled and prevents disabled-tile launch. |
| UXR restore | Pass | Speed 0, PARK and parking brake true restore Parked; final UXR is baseline. |
| QuickStep/SystemUI | Pass | KEYCODE_APP_SWITCH opens custom CarRecentsActivity; overview toggle returns to the top task. |
| Recents snapshot | Pass | Settings renders a real 960x540 task snapshot after adding READ_FRAME_BUFFER. |
| Recents open/remove/clear | Pass | Card tap opens Settings; Remove reaches No recent tasks; clear-all was checked with multiple tasks. |
| Calm Mode QC | Pass | SystemUI binds the provider and displays Calm mode; tapping it opens custom CalmModeActivity. |
| Calm Mode | Pass | Clock/date, Radio state, playback controls, Media Center and Close render and operate. |
| WidgetHost | Pass | WidgetHostActivity binds and renders the Date widget as Saturday / August 8 without an invalid widget ID. |
| MapTos | Pass | The explicit component opens the fallback and its Apps action opens App Grid. |
| InCall/ControlBar | Contract pass | PackageManager resolves InCallService and explicit activities; no live call source exists on the AVD. |
| Secondary display | Pass with limit | A trusted 1280x720 overlay display receives App Grid on display 2 with correct layout/task displayId; the overlay was removed afterward. |
| Stability | Pass | No FATAL EXCEPTION, NoClassDefFoundError or SecurityException in final smoke runs. |

Driving state was injected directly through VHAL:

~~~bash
adb shell cmd car_service inject-vhal-event PARKING_BRAKE_ON 0 false
adb shell cmd car_service inject-vhal-event GEAR_SELECTION 0 8
adb shell cmd car_service inject-vhal-event PERF_VEHICLE_SPEED 0 10.0
~~~

Speed 0, PARK and parking brake true were restored afterward.

## Clean boot / TaskView

Old AVD data initially prevented CarSystemUIProxy registration. The AVD was
booted once with -wipe-data and -no-snapshot to remove stale package state. On
the clean boot, CarSystemUIProxy registered=true and TaskView passed. This wipe
affected test AVD data only, not source or the system image; user 10 is now
created and unlocked normally.

## Final artifacts

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| app/build/outputs/apk/debug/app-debug.apk | 20,369,269 bytes | ab8f1727d6cde401dd36522726013e3c46026c10830bc976e10a704e30d1e1d5 |
| app/build/outputs/apk/release/app-release.apk | 15,840,498 bytes | 75ea13f8941cecd96aff32f8e0cbf50b3cccd14ee4f4bdb5c57a4652f253b478 |

Both APKs pass apksigner verification with APK Signature Scheme v2 and use the
matching image platform certificate.

## Remaining boundaries

- Overwrite installation requires the same platform certificate and compatible
  privileged-permission allowlist.
- Hidden APIs and AAOS shared JARs come from this image's workspace build; a new
  Android branch or product needs a new audit.
- The AVD has no real passenger/occupant-zone display. The overlay verifies
  routing/layout, not end-to-end multi-display hardware behavior.
- There is no live Telecom call or metadata-rich media provider to cover every
  call transition, artwork and queue path.
- The custom surface is not pixel-identical to every DEWD/OEM weather,
  assistive or card overlay. Those are not missing contracts on this AVD.
