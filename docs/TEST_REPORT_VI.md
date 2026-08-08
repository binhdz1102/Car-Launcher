# Báo cáo audit và kiểm thử Car-Launcher trên AVD

Ngày kiểm thử cuối: 2026-08-08

## Kết luận

**Đạt điều kiện thay thế launcher mặc định trên đúng AVD Automotive đã audit.**

APK custom giữ package, platform signature, privileged permissions và toàn bộ
launcher-owned component contract của APK stock. HOME, App Grid, TaskView,
Media, Recents/QuickStep, Calm Mode QC, WidgetHost, Map fallback và reset App
Grid đều đã được chạy trực tiếp trên máy ảo. Bản cuối đang được cài dưới dạng
system-app update trong /data/app; APK gốc vẫn nằm ở /system làm fallback.

Kết luận này không tự động áp dụng cho image/OEM khác có certificate, platform
JAR, overlay hoặc component contract khác.

## Môi trường

| Thuộc tính | Giá trị |
| --- | --- |
| Serial | emulator-5554 |
| AVD | my_car_avd_mysystemapp_20260802 |
| Fingerprint | Android/sdk_car_mysystemapp_x86_64/emulator_car64_x86_64:Baklava/CP2A.260605.016/eng.binh:userdebug/test-keys |
| Hệ điều hành | Android 17, API 37, userdebug |
| User | Automotive user 10 |
| Màn hình chính | 1920x1080, density 213 |
| Package | com.android.car.carlauncher |
| Stock | versionCode 37, versionName Baklava, /system/priv-app/CarLauncher |
| Custom | versionCode 1000, versionName custom-dev, minSdk 34, targetSdk 36 |
| Trạng thái xe cuối | Parked, UXR baseline |

Platform certificate SHA-256 của cả debug/release:

~~~text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
~~~

## Audit APK và contract

Manifest nhị phân của /system/priv-app/CarLauncher/CarLauncher.apk được so với
APK custom. Các component launcher-owned sau đều có implementation tương ứng:

- CarLauncher;
- AppGridActivity và action ACTION_APP_GRID;
- ResetLauncherActivity;
- CarRecentsActivity và CarQuickStepService;
- ControlBarActivity và WidgetHostActivity;
- CalmModeActivity và CalmModeQCProvider;
- MapTosActivity;
- InCallServiceImpl;
- DateAppWidgetProvider.

Các provider/activity do thư viện hỗ trợ stock tự merge như CarUiInstaller,
SearchResultsProvider hoặc WindowManager Shell desktop plumbing không phải
contract launcher mà SystemUI của image gọi. Bản custom không dùng Car UI
library đó và cung cấp UI/XML cùng chức năng trực tiếp.

SystemUI/RRO của image được kiểm tra thêm để xác nhận các reference thực tế:
AppGrid explicit component, QuickStep service, CarRecentsActivity và URI
content://com.android.car.carlauncher.calmmode/calm_mode.

## Build và kiểm thử tự động

Quality gate cuối:

~~~text
bash ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest   :app:assembleDebug :app:assembleRelease --console=plain --max-workers=2

BUILD SUCCESSFUL
586 actionable tasks
~~~

Instrumentation trên AVD:

~~~text
bash ./gradlew :app:connectedDebugAndroidTest --console=plain --max-workers=2

Starting 6 tests on my_car_avd_mysystemapp_20260802(AVD) - 17
Finished 6 tests on my_car_avd_mysystemapp_20260802(AVD) - 17
BUILD SUCCESSFUL
~~~

Sáu test kiểm tra package identity, critical granted permissions, HOME/App Grid,
các activity distraction-optimized, QuickStep/InCall services, Calm Mode
provider, Date widget và ResetLauncher Settings entry.

## Cài đè

Script được giữ trong source và đã chạy thành công:

~~~text
bash scripts/install-avd.sh emulator-5554
Performing Streamed Install
Success
Active replacement: package:/data/app/.../base.apk (versionCode=1000)
~~~

Script dùng adb install -r, không cần -d. Nó kiểm tra device, active path và
versionCode trước khi khởi động HOME. Sau connectedDebugAndroidTest phải chạy
script lại vì Gradle gỡ target APK sau test; bước này đã được thực hiện ở trạng
thái cuối.

Dumpsys xác nhận các permission sau được cấp cho bản update:

- android.permission.MANAGE_ACTIVITY_TASKS;
- android.permission.START_TASKS_FROM_RECENTS;
- android.permission.READ_FRAME_BUFFER;
- android.permission.BIND_APPWIDGET.

## Ma trận kiểm thử trực tiếp

| Khu vực | Kết quả | Bằng chứng quan sát |
| --- | --- | --- |
| HOME resolution | Pass | HOME resolve tới com.android.car.carlauncher/.CarLauncher; versionCode 1000 chạy từ /data/app. |
| HOME layout/insets | Pass | Media pane và embedded pane hiển thị trong vùng 1920x1080, không bị status/navigation bar che. |
| Car service | Pass | State chuyển ready=true sau launch; không block UI. |
| Media | Pass | Radio source/session được nhận; empty state, play/pause, previous/next, source list và Media Center hoạt động theo capability của session. |
| TaskView | Pass | CarTaskViewController kết nối; ControlledRemoteCarTaskView tạo SurfaceView, initialize và nhận onTaskAppeared cho Maps Placeholder. |
| App Grid qua SystemUI | Pass | Tap nút grid ở system bar mở đúng custom AppGridActivity. |
| App discovery | Pass | LauncherApps và media service tiles xuất hiện sau khi bỏ QUERY_ALL_PACKAGES; Settings/media tile launch được. |
| Search | Pass | Tìm theo label lọc danh sách; keyboard path chỉ có khi được UXR cho phép. |
| Reorder/persistence | Pass | Drag AdasLocationTestApp làm thứ tự thay đổi và vẫn giữ sau đóng/mở lại. |
| Reset App Grid | Pass | ResetLauncherActivity hiển thị dialog; OK xóa DataStore và thứ tự trở lại A–Z ngay. |
| UXR driving | Pass | Khi inject tốc độ/gear/brake ở trạng thái lái, search biến mất, non-DO app bị disable, media vẫn enabled và tap app bị khóa không launch. |
| UXR restore | Pass | Speed 0, gear PARK và parking brake true đưa xe về Parked; UXR cuối là baseline. |
| QuickStep/SystemUI | Pass | KEYCODE_APP_SWITCH mở custom CarRecentsActivity; overview toggle quay lại top task. |
| Recents snapshot | Pass | Settings có thumbnail snapshot 960x540 thật sau khi thêm READ_FRAME_BUFFER. |
| Recents open/remove/clear | Pass | Tap card mở Settings; Remove đưa list về No recent tasks; clear-all đã được kiểm tra với nhiều task. |
| Calm Mode QC | Pass | Quick Control panel của SystemUI bind provider và hiện Calm mode; tap row mở custom CalmModeActivity. |
| Calm Mode | Pass | Đồng hồ, ngày, Radio state, playback controls, Media Center và Close hiển thị/hoạt động. |
| WidgetHost | Pass | WidgetHostActivity bind và render Date widget với Saturday / August 8; không dùng INVALID widget ID. |
| MapTos | Pass | Explicit component mở đúng fallback; nút Apps đi tới App Grid. |
| InCall/ControlBar | Pass contract | PackageManager resolve đúng InCallService và các explicit activity; AVD không có cuộc gọi thật để kiểm tra call lifecycle. |
| Secondary display | Pass có giới hạn | Trusted overlay display 1280x720 nhận App Grid trên display 2, layout và task displayId đúng; overlay đã được gỡ sau test. |
| Stability | Pass | Không có FATAL EXCEPTION, NoClassDefFoundError hoặc SecurityException trong các smoke run cuối. |

UXR được kích trực tiếp bằng VHAL:

~~~bash
adb shell cmd car_service inject-vhal-event PARKING_BRAKE_ON 0 false
adb shell cmd car_service inject-vhal-event GEAR_SELECTION 0 8
adb shell cmd car_service inject-vhal-event PERF_VEHICLE_SPEED 0 10.0
~~~

Sau đó đã khôi phục speed 0, gear PARK và parking brake true.

## Clean boot / TaskView

Data cũ ban đầu làm CarSystemUIProxy không đăng ký. AVD đã được khởi động một lần
với -wipe-data và -no-snapshot để loại trạng thái package cũ. Trên clean boot,
CarSystemUIProxy registered=true và TaskView pass. Việc wipe chỉ áp dụng cho dữ
liệu AVD thử nghiệm, không thay đổi source hay image; hiện user 10 đã được tạo và
unlocked bình thường.

## Artifact cuối

| Artifact | Kích thước | SHA-256 |
| --- | ---: | --- |
| app/build/outputs/apk/debug/app-debug.apk | 20,369,269 bytes | ab8f1727d6cde401dd36522726013e3c46026c10830bc976e10a704e30d1e1d5 |
| app/build/outputs/apk/release/app-release.apk | 15,840,498 bytes | 75ea13f8941cecd96aff32f8e0cbf50b3cccd14ee4f4bdb5c57a4652f253b478 |

Cả hai APK đều pass apksigner verify bằng APK Signature Scheme v2 và cùng
platform certificate của image.

## Giới hạn còn lại

- Chỉ có thể cài đè khi image dùng cùng platform certificate và privileged
  permission allowlist tương thích.
- API ẩn/AAOS shared JAR được lấy từ workspace build của image này; phải audit
  lại khi chuyển branch Android hoặc product khác.
- AVD không có màn hình passenger/occupant-zone thật. Overlay display kiểm tra
  routing/layout, chưa phải validation end-to-end trên phần cứng nhiều màn hình.
- Không có cuộc gọi Telecom thật và media provider giàu metadata để bao phủ mọi
  artwork/queue/call transition.
- Giao diện custom không pixel-identical với mọi DEWD/OEM card, weather hoặc
  assistive overlay. Đây không phải contract bị thiếu trên AVD đã kiểm thử.
