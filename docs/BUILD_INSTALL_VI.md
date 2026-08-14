# Hướng dẫn build và cài đặt

## Điều kiện cần

- Windows PowerShell, JDK 17, Android SDK/build-tools API 37 và `adb`.
- Platform jar đúng output AOSP. `Launcher/` chỉ là source tham khảo và không
  được commit.
- AVD API 37 đã boot, user 10 và fingerprint khớp `baseline.lock.json`.

## Đồng bộ và kiểm tra artifact

Artifact platform được copy vào cây `platform-artifacts/api-37` bị Git ignore:

```powershell
.\scripts\sync-platform-artifacts.ps1 `
  -AospOut D:\path\to\aosp\out `
  -PlatformLibrariesDirectory D:\path\to\platform-libs
.\scripts\verify-platform-artifacts.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
```

Các lệnh trên kiểm checksum, SHA-256 baseline, package/version và certificate
platform; không in secret của keystore.

## Build và static gate

```powershell
.\gradlew.bat ktlintCheck detekt lintDebug testDebugUnitTest verifyApiCompat `
  :app:assembleDebug :app:assembleRelease :app:assembleAndroidTest `
  --no-daemon --console=plain --max-workers=2
.\scripts\verify-release-apk.ps1 `
  -ApkPath app\build\outputs\apk\release\app-release.apk
```

Certificate release phải là
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`.

## Cài candidate

```powershell
adb -s emulator-5554 install --no-streaming -r --user 10 `
  app\build\outputs\apk\release\app-release.apk
adb -s emulator-5554 shell am start --user 10 -W `
  -a android.intent.action.MAIN -c android.intent.category.HOME `
  -n com.android.car.carlauncher/.CarLauncher
```

`pm path` phải trỏ tới `/data/app/...`; package gốc vẫn ở
`/system/priv-app/CarLauncher`. Không được dùng key khác.

## Chạy parity baseline

Cài fixture và tạo snapshot khôi phục một lần:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot -InstallFixtures `
  -FixtureApk test-apps\fixture-app\build\outputs\apk\debug\fixture-app-debug.apk
```

Build AndroidTest rồi chạy full matrix. AndroidTest là bắt buộc, trừ smoke
không nghiệm thu có chỉ rõ `-SkipInstrumentation`:

```powershell
.\gradlew.bat :app:assembleAndroidTest --no-daemon --console=plain
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk `
  -InstrumentationApk app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Runner restore snapshot trước từng label/scenario, xóa toàn bộ logcat, kiểm
fixture/foreground/topology task, chạy instrumentation, lưu UI hierarchy và
screenshot, rồi restore snapshot khi `INVALID` hoặc comparison fail. Khi pass,
runner cài lại release và smoke HOME cuối. Xem
[AVD_PARITY_GUIDE_VI.md](AVD_PARITY_GUIDE_VI.md).

## Khôi phục APK gốc

```powershell
.\scripts\install-avd.ps1 -Serial emulator-5554 -UserId 10 -RestoreBaseline
```

Lệnh kiểm baseline đã khóa trước khi cài với `-r -d`. Rollback theo tag/commit
xem [ROLLBACK_VI.md](ROLLBACK_VI.md).
