# Car-Launcher AVD test report

Date: 2026-08-04  
Device: `emulator-5554`  
Image: AAOS automotive AVD, API 37 / Android 17, 1920x1080, density 213  
User: current automotive user 10

## Build and installation

The following command completed successfully:

```text
bash ./gradlew testDebugUnitTest :app:assembleDebug --console=plain --max-workers=2
BUILD SUCCESSFUL
```

The domain state-machine unit test passed. The APK was installed with:

```text
adb -s emulator-5554 install -r -d app/build/outputs/apk/debug/app-debug.apk
Success
```

The installed package is `com.android.car.carlauncher`, version `custom-dev`
with versionCode `1000`. Its platform certificate matches the image certificate
(`c8a2e9bc...1192ab8`), and the required privileged permissions remained
granted after installation, including `ACTIVITY_EMBEDDING`,
`INTERNAL_SYSTEM_WINDOW`, `MANAGE_ACTIVITY_TASKS`, and
`START_TASKS_FROM_RECENTS`.

## Function verification

| Area | Result | Evidence / notes |
| --- | --- | --- |
| HOME activity | Pass | HOME launch displayed the XML split layout and media empty state. The activity stayed the active HOME component. |
| Media empty state | Pass | The card displayed `Radio`, `Nothing playing`, album-note artwork, disabled skip controls, and the play control. |
| App grid entry | Pass | AOSP action opened `AppGridActivity`; UI hierarchy contained the app grid and 22 launchable items on this image. |
| App grid layout | Pass | Clean screen showed the search field, Navigation tile, four-column XML grid, icons, and labels. |
| App search | Pass | Typing `Camera` changed the field to `Camera` and left one `Camera` result. |
| Application selection | Pass | Selecting Camera returned to the existing HOME activity with `com.android.camera2/com.android.camera.CameraLauncher`; Timber recorded the target selection. |
| Navigation selection | Pass | Selecting the Navigation tile returned to HOME with the Maps Placeholder target. |
| App grid Back/close | Pass | System Back returned to the HOME component. The explicit Close view is wired to the same finish path. |
| Recents surface | Pass | The recents action opened `CarRecentsActivity`; the XML list displayed `MySystemApp`, `Gallery`, per-item `Remove`, and `Clear all`. |
| QuickStep contract | Build/integration pass | The service compiles against the image's `ILauncherProxy` shared contract and is declared with `QUICKSTEP_SERVICE`. Direct SystemUI callback verification is blocked by the custom SystemUI image described below. |
| TaskView host | Limited / blocked by image | The launcher requested `CarTaskViewController`, but `dumpsys activity service com.android.car/.CarService` reported `CarSystemUIProxy registered: false`. After the new bounded timeout, the real UI showed `TaskView unavailable` with `CarSystemUI did not register the car activity proxy on this device.` |
| TaskView retry/active embedded task | Not passable on this image | The AOSP TaskView service cannot deliver `onConnected` until the proxy is registered. The host and error path are implemented, but no honest active TaskView success claim can be made for this AVD. |
| Active media playback | Not exercised | The running image had no active media session/source. Empty media behavior was exercised; playback callbacks need a media provider/session to verify. |

## TaskView limitation

The current image runs the custom `custom-system-apps/Car-System-UI`. Its
`SystemUIService` starts the custom system-bar coordinator but does not create
the AOSP `CarSystemUIInitializer` / `CarSystemUIProxyImpl`. CarService
therefore reports:

```text
CarSystemUIProxy registered: false
```

This is an image-level dependency required by the original AOSP CarLauncher as
well. Car-Launcher does not modify the user's Car-System-UI repository. On an
image with the standard AOSP proxy, the `ControlledRemoteCarTaskView` callback
case should be re-run for Navigation and a selected application.

## Remaining limitations

- Full TaskView rendering and task lifecycle cannot be validated until the AVD
  registers `CarSystemUIProxy`.
- Active media source switching, playback controls, artwork updates, and seek
  behavior require an installed media session provider; only the empty state was
  available during this run.
- The standalone APK depends on platform jars produced by this workspace's
  matching AOSP build; it is not a generic Play-device application.
- The AVD restarted during testing, so the initial-user notice from the custom
  SystemUI may cover the launcher until dismissed. This is external to the
  launcher and is not considered an app crash.
