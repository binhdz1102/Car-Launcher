# Car-Launcher

`Car-Launcher` is a standalone, Gradle-built replacement for the AAOS
`com.android.car.carlauncher` system application. It keeps the AOSP launcher
contracts used by the automotive image while making the application practical
to iterate on from Android Studio.

The implementation is based on the current AOSP `packages/apps/Car/Launcher`
behavior and the Launcher-related work in `custom-system-apps/My-System-App`.
The UI is XML-based, including the home screen, media pane, embedded-task pane,
app grid, and recents surface.

## What is included

- Automotive HOME activity with the AOSP launcher component name.
- Media card backed by `CarMediaManager` and `MediaSessionManager`.
- Navigation and application embedding through
  `ControlledRemoteCarTaskView`.
- XML application grid with LauncherApps discovery, search, Navigation tile,
  distraction-optimization filtering, and user-specific ordering persisted with
  DataStore.
- XML recents activity and a QuickStep binder service compatible with the
  platform SystemUI shared contract.
- Hilt dependency injection, ViewModel state holders, Kotlin Coroutines and
  Flow, Timber logging, and a domain state machine with unit tests.

See the function descriptions in [English](docs/FUNCTIONS_EN.md) and
[Vietnamese](docs/FUNCTIONS_VI.md). The real-device verification is recorded in
the [English report](docs/TEST_REPORT_EN.md) and [Vietnamese report](docs/TEST_REPORT_VI.md).

## Module structure

```text
app/
  CarLauncher, CarRecentsActivity, CarQuickStepService, app resources
core/
  common       shared coroutine dispatchers
  ui           XML-oriented car UI helpers and tokens
feature/launcher/
  domain      models, repository contracts, pane state machine, unit tests
  data        LauncherApps, Car UX, media, DataStore implementations
  presentation ViewModels, XML fragments/activities, TaskView host, adapters
build-logic/  convention plugins for application, library, feature, Hilt, etc.
```

The dependency direction is `presentation -> domain`, `data -> domain`, and
the application assembles the feature. Android and AAOS APIs are kept at the
outer data/presentation boundaries. The simple launcher ordering state uses
DataStore; Room would add no value for the current data model. AndroidX
Navigation is not forced into the two entry surfaces because the AOSP launcher
contract is activity/action based and the embedded pane is a state machine.

## Build

From this directory:

```bash
bash ./gradlew testDebugUnitTest :app:assembleDebug --console=plain --max-workers=2
```

The standalone build expects these existing workspace artifacts:

- `../My-System-App/libs/platform/android.car.jar`
- `../My-System-App/libs/platform/framework.jar`
- `../../out-avd-car-mysystemapp/soong/.../SystemUISharedLib.jar`
- `../../out-avd-car-mysystemapp/soong/.../WindowManager-Shell-aidls.jar`

The debug APK is signed with the development platform certificate in
`keystore/platform.p12`. This key is only for the matching development image,
not for a production device.

## Install and launch on the development AVD

```bash
adb -s emulator-5554 install -r -d app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -W \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME \
  -n com.android.car.carlauncher/.CarLauncher
```

The application uses the same package and launcher component as AOSP, so an
`adb install -r -d` update replaces the active package for the current user
without changing the system image. Confirm the expected platform certificate
with:

```bash
adb -s emulator-5554 shell dumpsys package com.android.car.carlauncher
```

Expected certificate SHA-256:

```text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```

Open the app grid directly:

```bash
adb -s emulator-5554 shell am start -W \
  -a com.android.car.carlauncher.ACTION_APP_GRID \
  -p com.android.car.carlauncher
```

Open recents directly:

```bash
adb -s emulator-5554 shell am start -W \
  -a com.android.car.carlauncher.recents.OPEN_RECENT_TASK_ACTION \
  -n com.android.car.carlauncher/.recents.CarRecentsActivity
```

## TaskView prerequisite

The embedding API requires the automotive `CarSystemUIProxy` to be registered
by CarSystemUI. The current AVD uses the custom `Car-System-UI` application,
which reports `CarSystemUIProxy registered: false`; therefore the launcher
shows a bounded `TaskView unavailable` error on that image. This is an image
integration limitation, not a reason to silently claim TaskView success. With
the standard AOSP CarSystemUI proxy enabled, the same host creates and manages
the controlled remote task view.

## Restore the image package

Only use this when the development APK should be removed from the current user:

```bash
adb -s emulator-5554 install -r -d \
  "/home/binh/Desktop/aosp/custom-system-apps/original system apks/CarLauncher.apk"
adb -s emulator-5554 shell am force-stop com.android.car.carlauncher
```
