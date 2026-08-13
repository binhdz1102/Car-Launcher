# Hướng dẫn build và cài đặt

## Điều kiện cần

- Windows PowerShell, JDK 17 và Gradle wrapper của repository.
- Android SDK API 37/build-tools và `adb` trong `PATH`.
- Cây output AOSP đúng branch và platform library cục bộ. Không commit
  `Launcher/` hoặc platform artifact sinh ra.
- AVD API 37 đã boot, fingerprint và user 10 khớp `baseline.lock.json`.

## Đồng bộ và kiểm artifact platform

Build dùng `android.car.jar`, `framework.jar`, SystemUI shared, WindowManager
Shell và car-qc jar từ đúng output AOSP. Đồng bộ vào thư mục Git ignore
`platform-artifacts/api-37`:

```powershell
.\scripts\sync-platform-artifacts.ps1 `
  -AospOut D:\path\to\out-avd-car-mysystemapp `
  -PlatformLibrariesDirectory D:\path\to\My-System-App\libs\platform
.\scripts\verify-platform-artifacts.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
```

Lệnh sync ghi bundle ignore và `platform-artifacts.lock.json`. Mỗi checksum
phải được sinh lại từ cùng output AOSP; không thay jar của API khác.
`baseline.lock.json` khóa SHA-256 APK gốc, certificate, package, version và
fingerprint AVD.

## Build và kiểm APK

```powershell
.\gradlew.bat ktlintCheck detekt lintDebug testDebugUnitTest `
  :app:assembleDebug :app:assembleRelease --console=plain --max-workers=2
.\scripts\verify-release-apk.ps1 `
  -ApkPath app\build\outputs\apk\release\app-release.apk
```

Release phải ký bằng certificate
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`. Script
chỉ in SHA-256 artifact, không log secret keystore.

## Cài làm launcher update

Dùng installer có kiểm tra package path và user:

```powershell
.\scripts\install-avd.ps1 -Serial emulator-5554 -UserId 10 -Build
```

Hoặc cài release đã build:

```powershell
adb -s emulator-5554 install --no-streaming -r `
  app\build\outputs\apk\release\app-release.apk
adb -s emulator-5554 shell am start --user 10 -W `
  -a android.intent.action.MAIN -c android.intent.category.HOME `
  -n com.android.car.carlauncher/.CarLauncher
```

`pm path` phải trả `/data/app/...`; APK gốc vẫn nằm ở
`/system/priv-app/CarLauncher`. Release dùng platform certificate, vì vậy
không cài APK ký bằng key khác lên package stock.

## So sánh baseline trước nghiệm thu

Cài fixture deterministic, tạo snapshot một lần rồi chạy full matrix:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot -InstallFixtures `
  -FixtureApk test-apps\fixture\build\outputs\apk\debug\fixture-debug.apk
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk
```

Runner restore `car_launcher_parity_ready` trước mỗi label, capture sáu
scenario, ghi `run.json` và restore snapshot khi fail. Nếu pass, runner cài lại
release candidate và smoke HOME cuối. Xem [AVD parity guide](AVD_PARITY_GUIDE_VI.md)
để biết mask và ngưỡng nghiệm thu.

## Khôi phục APK gốc

```powershell
.\scripts\install-avd.ps1 -Serial emulator-5554 -UserId 10 -RestoreBaseline
```

Lệnh kiểm baseline đã khóa trước rồi cài với `-r -d`. Quy trình đầy đủ xem
[ROLLBACK_VI.md](ROLLBACK_VI.md).
