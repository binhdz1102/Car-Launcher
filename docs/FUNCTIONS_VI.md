# Chức năng của Car-Launcher

Tài liệu này mô tả chức năng của bản thay thế package
com.android.car.carlauncher trên image AVD Automotive đã kiểm thử. Các component
public của launcher gốc được giữ nguyên; phần triển khai nội bộ dùng kiến trúc
multi-module hiện đại.

## HOME và vòng đời dịch vụ xe

CarLauncher xử lý MAIN, HOME, SECONDARY_HOME, DEFAULT và LAUNCHER_APP. Activity
giữ singleTask, stateNotNeeded, resumeWhilePausing và contract
ALLOW_HOME_ACTIVITY_ALWAYS_PRESENT tương đương launcher hệ thống.

HOME gồm thẻ media bên trái và vùng ứng dụng nhúng bên phải. Mọi cửa sổ tự áp
system-bar insets để không bị status/navigation bar che. CarServiceConnection là
kết nối dùng chung, không chặn main thread, phát trạng thái bằng StateFlow và tự
kết nối lại khi dịch vụ xe chết hoặc sẵn sàng trở lại.

## Media

MediaRepository kết hợp CarMediaManager, MediaSessionManager và callback
MediaController. UI hỗ trợ:

- tự nối lại source và session hiện hành;
- title, artist, artwork, vị trí và thời lượng;
- previous, play/pause, next và seek;
- danh sách/chuyển media source;
- mở Media Center theo source;
- empty state khi source không có metadata hoặc session.

Calm Mode dùng chung repository nên trạng thái và thao tác playback nhất quán với
HOME.

## Navigation và TaskView

Ứng dụng bản đồ mặc định được tìm theo MAIN/CATEGORY_APP_MAPS của user hiện tại.
ControlledRemoteCarTaskView nhúng activity vào pane phải, cập nhật bounds theo
layout, giữ target qua vòng đời HOME và khôi phục khi task biến mất.

State machine quản lý Idle, Loading, Running và Error. Request có timeout hữu hạn;
khi CarSystemUIProxy hoặc Car service chưa sẵn sàng, UI báo lỗi có thể retry thay
vì treo hoặc crash. Sau clean boot của image mục tiêu, Maps Placeholder tạo
SurfaceView/task thật và callback onTaskAppeared hoạt động.

MapTosActivity giữ component fallback mà scalable SystemUI yêu cầu và cho phép
mở App Grid khi chưa cấu hình bản đồ.

## App Grid, an toàn khi lái và reset

App Grid được mở bằng action
com.android.car.carlauncher.ACTION_APP_GRID hoặc component gốc
com.android.car.carlauncher/.AppGridActivity. Activity dùng singleInstance giống
APK stock.

Danh sách lấy từ LauncherApps của user hiện tại và các MediaBrowserService được
công bố. `QUERY_ALL_PACKAGES` được giữ vì hợp đồng AOSP CarLauncher lập danh
mục toàn bộ ứng dụng có thể khởi chạy; permission này được suppress cục bộ
trong lint với chú thích giải thích. RecyclerView hỗ trợ:

- tile Navigation;
- icon, label và thao tác mở activity/media app;
- tìm kiếm tức thời;
- kéo-thả để đổi thứ tự khi xe đỗ;
- lưu thứ tự bằng Preferences DataStore;
- phát hiện package thêm/xóa/thay đổi;
- DiffUtil để cập nhật mà không dựng lại toàn bộ danh sách.

CarUxRestrictions được kiểm tra ở cả lúc render và ngay trước khi launch. Khi mô
phỏng đang chạy xe, ô tìm kiếm bị ẩn, kéo-thả bị khóa và app không có
distractionOptimized bị vô hiệu hóa; media app/DO app vẫn hoạt động. Nếu dịch vụ
an toàn không sẵn sàng, trạng thái fail-safe khóa launch không xác minh được.

ResetLauncherActivity xuất hiện trong mục Apps của Automotive Settings. Activity
hỏi xác nhận, xóa thứ tự DataStore và App Grid trở về A–Z ngay lập tức.

## Recents và QuickStep

CarQuickStepService triển khai ILauncherProxy đúng shared contract của SystemUI
trên image. Nút overview hoặc KEYCODE_APP_SWITCH mở CarRecentsActivity; gọi lại
khi overview đang mở sẽ quay về task trên cùng.

RecentTasksRepository dùng IRecentTasks của WindowManager Shell và có fallback
ActivityManager. Danh sách:

- chỉ lấy task của display hiện hành;
- loại launcher, SystemUI và permission controller;
- đọc label, icon và task snapshot;
- mở task bằng startActivityFromRecents;
- xóa từng task hoặc clear all;
- phát trạng thái qua StateFlow.

Permission READ_FRAME_BUFFER được khai báo và cấp nên thumbnail là snapshot thật,
không phải placeholder.

## Các component tương thích với image

Ngoài HOME/App Grid/Recents, APK công bố đầy đủ các component launcher-owned có
trong manifest stock:

- ControlBarActivity và WidgetHostActivity: host AppWidget, hiện Date widget mặc
  định và giữ ID widget;
- DateAppWidgetProvider: cập nhật theo ngày, giờ và timezone;
- CalmModeQCProvider tại
  content://com.android.car.carlauncher.calmmode/calm_mode, chỉ cho SystemUI bind;
- CalmModeActivity: màn hình ít gây xao nhãng với đồng hồ và media controls;
- MapTosActivity: fallback bản đồ có thể embed;
- InCallServiceImpl: contract Telecom; call UI vẫn thuộc CarSystemUI như APK gốc;
- ResetLauncherActivity: entry reset App Grid trong Automotive Settings.

Các privileged permission quan trọng như MANAGE_ACTIVITY_TASKS,
START_TASKS_FROM_RECENTS, READ_FRAME_BUFFER và BIND_APPWIDGET đã được kiểm tra là
granted sau khi cài đè.

## Kiến trúc

- domain chứa model, interface repository và state machine không phụ thuộc UI;
- data cô lập Car API, LauncherApps, media, WindowManager Shell và DataStore;
- presentation dùng ViewModel, immutable UI state và lifecycle-aware Flow;
- app chỉ lắp ghép entry point/framework contract;
- Hilt quản lý dependency; Coroutines/Flow thay polling và worker thread tự quản;
- Java/Kotlin 17, minSdk 34, compileSdk 37, targetSdk 37.

Room không phù hợp với một chuỗi thứ tự nhỏ; AndroidX Navigation không cần thiết
vì platform đi vào app bằng activity/action cố định.

## Ranh giới xác nhận

Bản migration này vẫn đang trong giai đoạn audit và chưa được nghiệm thu để
thay launcher: run hiện tại còn thiếu chức năng và fail strict screenshot parity.
Việc nghiệm thu phải đạt các release gate trong `TEST_REPORT_VI.md` và ma trận
source/API/resource đầy đủ. AVD chỉ có một
occupant display thật; overlay display 1280x720 đã kiểm tra layout/routing nhưng
chưa thay thế được bài test passenger occupant-zone trên phần cứng nhiều màn
hình. Không có cuộc gọi Telecom thật hoặc media provider giàu metadata trong
image, nên các contract tương ứng được kiểm tra ở mức bind/resolve và session có
sẵn.
