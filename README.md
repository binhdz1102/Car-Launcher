# Car-Launcher

Car-Launcher is a standalone, Gradle-built replacement for the AAOS package
com.android.car.carlauncher. It keeps the public component, permission, signing,
HOME, SystemUI, QuickStep, widget and Settings contracts used by the matching
AOSP Automotive image while using a modern multi-module implementation.

The package contract, platform signing and several feature slices are migrated,
and the APK can be installed as a `/data/app` update. This is not yet an
accepted stock replacement: the current AVD run is fail-closed because the
baseline HOME capture produced an ANR, and strict screenshot parity remains
open for all surfaces until a clean three-run matrix passes.

## Replacement status

The stock APK manifest and the SystemUI overlay on the target image were audited.
The replacement publishes every launcher-owned component consumed by that image:

| Platform contract | Replacement |
| --- | --- |
| HOME / SECONDARY_HOME | CarLauncher |
| App drawer action and stock explicit component | AppGridActivity plus the .AppGridActivity compatibility alias |
| Overview / recents | CarRecentsActivity and CarQuickStepService |
| Launcher control/widget surface | ControlBarActivity and WidgetHostActivity |
| Calm Mode Quick Control | CalmModeQCProvider and CalmModeActivity |
| Map fallback | MapTosActivity |
| Telecom binding | InCallServiceImpl |
| Date widget | DateAppWidgetProvider |
| Automotive Settings reset entry | ResetLauncherActivity |

The implementation covers:

- XML HOME surface with reconnecting media and embedded map/application panes;
- CarMediaManager and MediaSessionManager source/session observation, playback,
  seek, previous/next, source switching and Media Center launch;
- ControlledRemoteCarTaskView lifecycle and recovery;
- LauncherApps-based application discovery, media-service tiles, search,
  persisted drag ordering and reset-to-A-Z;
- fail-safe Car UX restrictions, including disabled non-DO apps and removal of
  keyboard/reorder operations while driving;
- platform task snapshots, open/remove/clear recents and the SystemUI QuickStep
  binder;
- SystemUI App Grid and Calm Mode QC integration;
- system-window insets, user/display-aware routing and secondary-display layout.

## Documentation

- [Architecture and data flow (EN)](docs/ARCHITECTURE_EN.md) / [VI](docs/ARCHITECTURE_VI.md)
- [Migration matrix (EN)](docs/MIGRATION_MATRIX_EN.md) / [VI](docs/MIGRATION_MATRIX_VI.md)
- [Build and install (EN)](docs/BUILD_INSTALL_EN.md) / [VI](docs/BUILD_INSTALL_VI.md)
- [AVD parity procedure (EN)](docs/AVD_PARITY_GUIDE_EN.md) / [VI](docs/AVD_PARITY_GUIDE_VI.md)
- [Rollback (EN)](docs/ROLLBACK_EN.md) / [VI](docs/ROLLBACK_VI.md)
- [Audited remediation status (EN)](docs/AUDIT_REMEDIATION_EN.md) / [VI](docs/AUDIT_REMEDIATION_VI.md)
- [Latest test report (EN)](docs/TEST_REPORT_EN.md) / [VI](docs/TEST_REPORT_VI.md)

The full module graph and MVVM/Clean boundaries are maintained in the
architecture document. Hilt, ViewModel, Coroutines/Flow, lifecycle-aware
collection, DataStore, DiffUtil and explicit state machines are used at their
appropriate boundaries.

Build settings are Java/Kotlin 17, minSdk 34, compileSdk 37 and targetSdk 37.
The implementation is verified on Android 17/API 37.

## Build

The standalone build uses platform artifacts from this AOSP workspace:

- ../My-System-App/libs/platform/android.car.jar
- ../My-System-App/libs/platform/framework.jar
- SystemUISharedLib and WindowManager Shell JARs under
  ../../out-avd-car-mysystemapp/soong
- car-qc-lib from the same Soong output

Run the complete local quality gate:

~~~powershell
.\gradlew.bat ktlintCheck detekt lintDebug testDebugUnitTest verifyApiCompat `
  :app:assembleDebug :app:assembleRelease --console=plain --max-workers=2
~~~

Run the component contract tests on a booted AVD:

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain --max-workers=2
~~~

The connected test task temporarily installs and then removes the target APK.
Run the installation script again afterward so the custom update remains active.

## Install over the prebuilt AVD launcher

The retained installation workflow is:

~~~bash
bash scripts/install-avd.sh emulator-5554
~~~

The script builds the debug APK when missing, performs adb install -r, verifies
that the active package comes from /data/app with versionCode 1000, force-stops
the package and starts its HOME activity. No downgrade flag is needed because
1000 is greater than the image launcher versionCode 37.

An explicit APK path can be supplied as the second argument:

~~~bash
bash scripts/install-avd.sh emulator-5554   app/build/outputs/apk/release/app-release.apk
~~~

The equivalent manual command is:

~~~bash
adb -s emulator-5554 install -r   app/build/outputs/apk/debug/app-debug.apk
~~~

Both debug and release variants use the development image's platform
certificate. Expected certificate SHA-256:

~~~text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
~~~

Useful checks:

~~~bash
adb -s emulator-5554 shell pm path com.android.car.carlauncher
adb -s emulator-5554 shell dumpsys package com.android.car.carlauncher
adb -s emulator-5554 shell cmd package resolve-activity --brief   -a android.intent.action.MAIN -c android.intent.category.HOME
~~~

## Direct entry points

~~~bash
adb -s emulator-5554 shell am start -W   -a com.android.car.carlauncher.ACTION_APP_GRID   -p com.android.car.carlauncher

adb -s emulator-5554 shell input keyevent KEYCODE_APP_SWITCH

adb -s emulator-5554 shell am start -W   -n com.android.car.carlauncher/.ResetLauncherActivity
~~~

## Verification scope

Direct AVD results, including UXR injection, SystemUI integration, TaskView,
recents snapshots, reset ordering and the secondary-display check, are recorded
in [the English test report](docs/TEST_REPORT_EN.md) and
[the Vietnamese test report](docs/TEST_REPORT_VI.md). The retained
pre-remediation failure is under `artifacts/parity/run-20260813-204544`; the
latest audit attempt is under `artifacts/parity/audit-run-home` and is
`INVALID` because the baseline HOME log window contains an AVD ANR. These
artifact directories are ignored by Git and are not acceptance evidence.

The exact AVD only exposes one physical occupant display. A trusted 1280x720
overlay display verified display routing and responsive layout, but it is not a
substitute for an end-to-end passenger occupant-zone test on multi-display
hardware. OEM-specific assistive/weather cards and pixel-level DEWD styling are
also product UI choices, not missing contracts on this AVD.

## Restore the image package

To replace the development update with the original matching APK:

~~~bash
adb -s emulator-5554 install --no-streaming -r -d `
  "Launcher\apk\CarLauncher.apk"
adb -s emulator-5554 shell am force-stop com.android.car.carlauncher
~~~
