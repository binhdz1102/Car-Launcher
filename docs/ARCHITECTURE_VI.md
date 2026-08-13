# Kiến trúc Car Launcher

Tài liệu này mô tả phần đã migrate; cây `Launcher/` chỉ là source tham khảo và
được Git ignore. Target chính thức là AVD API 37 trong `baseline.lock.json`.

## Ranh giới module

```text
app
├─ entry point platform, component manifest và màn hình XML
├─ core/{common,model,platform,ui,testing}
├─ feature/{launcher,home,media,appgrid,recents,calmmode,dock}/{domain,data,presentation}
├─ feature/widgets
├─ libraries/{appgrid,dock,dock-util,launcher-common}
├─ compat
└─ test-apps/fixture
```

`domain` chứa model bất biến và contract repository. `data` sở hữu adapter cho
Android, CarService, LauncherApps, MediaSession, TaskManager, DataStore và
protobuf. `presentation` sở hữu ViewModel, `StateFlow`/`SharedFlow`, activity,
fragment và adapter XML/ViewBinding. `app` là composition root và giữ nguyên
package/component/resource contract công khai.

Quy tắc phụ thuộc là `presentation -> domain`, `data -> domain`, và
`app -> presentation/data`. Một feature không truy cập implementation data cụ
thể của feature khác. Hilt bind implementation platform tại biên app.

## State và đồng thời

Callback Android được chuyển thành `callbackFlow` ở biên, sau đó repository và
ViewModel cung cấp `StateFlow`. UI collect theo lifecycle bằng
`repeatOnLifecycle`; coroutine được hủy theo structured concurrency. `Handler`
chỉ được phép nằm trong adapter khi API Android bắt buộc `Looper`, không dùng để
điều phối state của ứng dụng.

Luồng chuẩn:

```text
CarService / SystemUI / LauncherApps / MediaSession
        -> adapter callbackFlow
        -> state repository + persistence
        -> ViewModel.combine/stateIn
        -> XML collection và effect một lần qua SharedFlow
```

## HOME và TaskView

`CarLauncher` sở hữu cửa sổ HOME. `ControlledRemoteCarTaskView` chỉ được tạo sau
khi kết nối `CarSystemUIProxy`/CarService báo ready. Vòng đời cố định: create,
đăng ký callback task, initialize với display và launch options, nhận
`onTaskAppeared`, rồi xử lý remove/update/reconnect. Khi `onStop` hoặc mất service,
controller unregister và release view; khi ready lại thì tạo controller mới.
Maps Placeholder là fixture deterministic dùng trong bằng chứng AVD.

Home repository combine media, map/task, call/projection/assistive card và dock
order. Update task/media được reduce thành một UI state để reconnect không để
SurfaceView hoặc card cũ tồn tại.

## App Grid và UXR

Discovery của `LauncherApps` và media-service tile được chuẩn hóa thành domain
model. DataStore lưu drag order; format `files/order.data` của stock được đọc
trong giai đoạn migrate và dual-write để rollback không mất state. Search,
reset-to-A-Z, shortcut và launch là intent của ViewModel. UXR repository cung cấp
`StateFlow` cho Park/Drive; presentation disable tile và bỏ search/reorder khi
đang lái.

## Recents và QuickStep

Data adapter recents đọc task snapshot/metadata bằng platform API.
`CarRecentsActivity` nhận open/remove/clear qua ViewModel. `CarQuickStepService`
vẫn là binder entry point của SystemUI và route overview vào cùng recents state.
Việc đọc snapshot được bảo vệ bởi privileged permission trong manifest.

## Calm Mode và widget

Calm Mode dùng activity translucent để control bar SystemUI vẫn nằm trên nền đen,
đúng với window routing của stock. Clock/date là `TextClock` XML; nhiệt độ là
CarProperty callbackFlow có đổi C/F; media được combine trong
`CalmModeViewModel`. WidgetHost dùng `AppWidgetHost` với host ID ổn định và lưu
binding provider theo display. Date widget và empty state là fixture deterministic.

## Tương thích display và persistence

Mỗi surface lấy `DisplayTarget` hiện tại và áp dụng inset qua UI helper dùng
chung. Dock order được dual-write vào DataStore và file protobuf tương thích
stock; launcher order dùng cùng quy tắc. Vì vậy rollback về APK stock không làm
mất thứ tự app hoặc dock.

Platform keystore, artifact API 37, identity APK baseline và fingerprint AVD được
khóa ngoài source. Build sẽ kiểm checksum artifact và certificate release trước
khi cài đặt.
