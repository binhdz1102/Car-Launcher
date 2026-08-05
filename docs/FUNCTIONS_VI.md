# Chức năng của Car-Launcher

## Màn hình HOME

`CarLauncher` là HOME activity được export và giữ nguyên package/component
identity của launcher AOSP. Giao diện được viết bằng XML theo bố cục hai pane:

- pane bên trái là thẻ media;
- pane bên phải hiển thị navigation hoặc ứng dụng được chọn để embed;
- system bar của Android Automotive nằm ngoài cửa sổ của app.

Launcher nhận HOME action chuẩn và intent trả về từ app grid. Activity dùng
single-task để việc chọn app quay lại đúng instance HOME hiện có.

## Media

Media repository kết hợp media source của xe và callback của media session thành
các `StateFlow`. Thẻ XML hỗ trợ:

- tên source và popup chọn source;
- title, artist, artwork, vị trí, thời lượng và seek bar;
- previous, play/pause, next và media-center;
- empty state rõ ràng khi chưa có media session hoạt động.

Các API callback của Android chỉ dùng executor main bắt buộc tại biên platform;
việc truyền state tới UI dùng Flow và lifecycle collection.

## Navigation và ứng dụng embed

Data layer tìm activity navigation của user hiện tại qua
`ACTION_MAIN`/`CATEGORY_APP_MAPS`. Ứng dụng được phát hiện bằng `LauncherApps`,
sau đó lọc theo user hiện tại, loại package của chính launcher, các package
system không cần hiển thị và Car UX restriction. Target được quản lý bởi state
machine:

`Idle -> Loading -> Running`, hoặc `Loading/Running -> Error`.

Presentation layer sở hữu Android-specific
`ControlledRemoteCarTaskView`. Layer này cập nhật bounds theo container XML,
đổi visibility trong lúc tương tác với host, khởi động lại Navigation khi task
biến mất, và báo timeout hữu hạn khi car service không cung cấp task.

## App grid

`AppGridActivity` giữ action AOSP:
`com.android.car.carlauncher.ACTION_APP_GRID`. Đây là RecyclerView XML bốn cột,
bao gồm:

- ô tìm kiếm;
- tile Navigation;
- icon và label của từng ứng dụng;
- lọc ngay khi gõ;
- trả về HOME với extra chọn Navigation hoặc extra component/label của app;
- nút đóng và thao tác Back của hệ thống.

Thứ tự app được lưu bằng Preferences DataStore. Chưa dùng Room vì dữ liệu hiện
tại chỉ là một danh sách có thứ tự nhỏ, chưa phải dữ liệu quan hệ.

## Recents và QuickStep

`CarRecentsActivity` là RecyclerView XML đọc task từ platform task manager. Nó
loại task của Car-Launcher và SystemUI, hiển thị icon/label, mở task, xóa từng
task và cung cấp nút clear-all. `CarQuickStepService` triển khai contract
`ILauncherProxy` từ shared library của platform và chuyển overview request tới
màn hình recents này.

## Kiến trúc và chẩn đoán

Project dùng cấu trúc multi-module lấy cảm hứng từ NowInAndroid:

- domain model và repository interface không phụ thuộc UI Android;
- data adapter cô lập API Car/LauncherApps/media/DataStore;
- ViewModel phát immutable UI state và nhận event;
- Fragment/Activity XML render state và bind event;
- Hilt cung cấp dependency và ViewModel;
- Coroutine/Flow thay cho polling và worker thread tự quản lý;
- Timber log lifecycle, target selection, embedding, media, app grid và lỗi
  recents.

Kết quả kiểm thử AVD và các giới hạn tích hợp được ghi trong
`TEST_REPORT_VI.md`.
