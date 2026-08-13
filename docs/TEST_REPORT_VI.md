# Báo cáo audit và kiểm thử Car Launcher trên AVD

Ngày kiểm thử: 2026-08-13
Baseline: `Launcher/apk/CarLauncher.apk`
Full parity run: `artifacts/parity/run-20260813-204544` (Git ignore)

## Kết luận

Migration đã tương thích component và chức năng trên AVD API 37 được audit; APK
release ký platform cài được dưới dạng update `/data/app`. Tuy nhiên **chưa được
nghiệm thu là replacement pixel-parity**: golden gate full fail ở HOME, App Grid,
Recents, Widget Host và Map ToS. Calm Mode đã được sửa sau run này và pass so
sánh tập trung có mask. Các khác biệt UI còn lại là khác biệt layout/style thật,
không che bằng cách hạ ngưỡng gate.

## Môi trường và identity

| Thuộc tính | Giá trị |
| --- | --- |
| Serial / user | `emulator-5554` / 10 |
| API / fingerprint | 37 / `Android/sdk_car_mysystemapp_x86_64/emulator_car64_x86_64:Baklava/CP2A.260605.016/eng.binh:userdebug/test-keys` |
| Màn hình | 1920x1080, density 213 |
| Package | `com.android.car.carlauncher` |
| Stock | versionCode 37, `/system/priv-app/CarLauncher` |
| Candidate | versionCode 1000, `custom-dev`, update `/data/app` |
| SHA-256 baseline | `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` |
| Certificate | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| SHA-256 release candidate | `7bb94ae650624518d5e161849150ddcb30f03a69964a1bc679caa2d7f213622c` |
| Snapshot | `car_launcher_parity_ready` |

## Build và quality tĩnh

Các kiểm tra sau pass sau thay đổi Calm Mode:

```text
./gradlew :feature:calmmode:data:ktlintCheck
         :feature:calmmode:presentation:ktlintCheck
         :feature:calmmode:data:detekt
         :feature:calmmode:presentation:detekt
         :app:lintDebug testDebugUnitTest --console=plain --max-workers=2
./gradlew :app:assembleRelease --console=plain --max-workers=2
```

Certificate release được kiểm bởi `scripts/verify-release-apk.ps1`; không in
credential. Artifact release bàn giao có SHA-256
`7bb94ae650624518d5e161849150ddcb30f03a69964a1bc679caa2d7f213622c`.

## Contract parity

`compare-parity-contract.py` trả `passed: true` cho cả sáu scenario trong full
run. So sánh gồm manifest token thuộc launcher và resolution HOME, App Grid,
QuickStep cho user 10.

| Scenario | Contract | Screenshot full run | SSIM | Pixel khác biệt |
| --- | --- | --- | ---: | ---: |
| HOME | Pass | Fail | 0,6913 | 19,3307% |
| App Grid | Pass | Fail | 0,2184 | 11,4891% |
| Recents | Pass | Fail | 0,6913 | 19,3307% |
| Calm Mode (trước fix cuối) | Pass | Fail | 0,3561 | 1,3904% |
| Widget Host | Pass | Fail | 0,4249 | 4,2803% |
| Map ToS | Pass | Fail | 0,2278 | 90,4382% |

Follow-up Calm Mode đổi window sang translucent, khớp nền đen và giữ control bar
SystemUI. Capture tập trung sạch tại
`artifacts/parity/20260813-211333-candidate-calm-mode`, so với stock bằng mask
clock/date theo guide, đạt `SSIM 1.0` và `differentPixelRatio 0.0`; contract
cũng pass.

## Bằng chứng chức năng trên AVD

| Khu vực | Kết quả | Bằng chứng |
| --- | --- | --- |
| HOME và map TaskView | Pass spot check | callbackFlow CarService/SystemUI đạt ready; surface Maps Placeholder xuất hiện và đã kiểm reconnect. |
| Media và home card | Pass spot check | fixture media source/session, playback control và empty card render. |
| App Grid | Pass chức năng | discovery, search, reorder persistence, reset A-Z, shortcut và action App Grid. |
| UXR khi lái | Pass | search/reorder bị hạn chế, tile non-DO bị disable khi lái; Park khôi phục. |
| Recents/QuickStep | Pass chức năng | app-switch mở `CarRecentsActivity`; snapshot/open/remove/clear và QuickStep resolution hoạt động. |
| Calm Mode QC/activity | Pass | QC mở activity translucent; date, clock, nhiệt độ, media và control bar render. |
| WidgetHost/Date widget | Pass chức năng | Hilt bind ID hợp lệ và Date widget render. |
| Map ToS | Pass chức năng | explicit activity mở và Apps route sang App Grid. |
| Dock và persistence | Pass unit/contract | protobuf codec, DataStore dual-write và event contract pass. |
| Secondary display | Pass có giới hạn | overlay tin cậy display 2 nhận App Grid đúng routing; chưa phải phần cứng occupant-zone. |

## Ghi chú ổn định

Smoke sạch mới nhất đã clear logcat, force-stop package rồi start Calm Mode; không
có `FATAL EXCEPTION`, `SecurityException` hay lỗi process launcher. Một artifact
full run trước đó có race khi AVD đang cập nhật package (NPE
`ConfigurationController` lúc rebind process cũ), không phải lỗi tương tác màn
hình. Sau restart lỗi không lặp lại, nhưng release gate nên chạy lại full matrix
từ snapshot mới restore và logcat sạch.

## Việc còn lại và điều kiện nghiệm thu

Replacement có thể tiếp tục dùng cho test chức năng và phát triển rollback-safe.
Chưa được gọi pixel-parity hoàn chỉnh cho đến khi HOME media/map layout, visual
App Grid stock, Recents, WidgetHost và Map ToS đạt SSIM >= 0,98 và <= 2% pixel
khác biệt. Run nghiệm thu tiếp theo cũng phải có logcat sạch và record SHA/cert
release.
