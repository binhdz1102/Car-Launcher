# CarLauncher

Standalone Gradle-built development replacement for the
`com.android.car.carlauncher` system application in the matching AAOS
development emulator image.

The project is signed with the AOSP development platform certificate stored in
`keystore/platform.p12`. Do not use this key for a production image.

Build and install:

```bash
bash ./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.android.car.carlauncher/.CarLauncher
```

The project intentionally provides a small HOME screen for fast Android Studio
iteration. The full AOSP CarLauncher is Soong-built and has dependencies on
internal AAOS libraries; it is not claimed to be source-compatible here.

Restore the image APK used for testing:

```bash
adb -s emulator-5554 install -r -d \
  "/home/binh/Desktop/aosp/custom-system-apps/original system apks/CarLauncher.apk"
adb -s emulator-5554 shell am force-stop com.android.car.carlauncher
```

Expected platform certificate SHA-256:

```text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```
