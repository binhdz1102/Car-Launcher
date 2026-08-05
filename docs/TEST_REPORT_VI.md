# Báo cáo kiểm thử Car-Launcher trên AVD

Ngày: 2026-08-04  
Thiết bị: `emulator-5554`  
Image: AAOS automotive AVD, API 37 / Android 17, 1920x1080, density 213  
User: automotive user hiện tại 10

## Build và cài đặt

Lệnh sau hoàn thành thành công:

```text
bash ./gradlew testDebugUnitTest :app:assembleDebug --console=plain --max-workers=2
BUILD SUCCESSFUL
```

Unit test của domain state machine đã pass. APK được cài bằng:

```text
adb -s emulator-5554 install -r -d app/build/outputs/apk/debug/app-debug.apk
Success
```

Package cài trên máy là `com.android.car.carlauncher`, version `custom-dev`,
versionCode `1000`. Certificate platform của APK khớp certificate của image
(`c8a2e9bc...1192ab8`). Các privileged permission cần thiết vẫn được cấp sau
khi cài, gồm `ACTIVITY_EMBEDDING`, `INTERNAL_SYSTEM_WINDOW`,
`MANAGE_ACTIVITY_TASKS` và `START_TASKS_FROM_RECENTS`.

## Kết quả kiểm thử chức năng

| Khu vực | Kết quả | Bằng chứng / ghi chú |
| --- | --- | --- |
| HOME activity | Pass | HOME launch hiển thị split layout XML và media empty state; activity vẫn là HOME component active. |
| Media empty state | Pass | Card hiển thị `Radio`, `Nothing playing`, artwork nốt nhạc, skip control disabled và play control. |
| Mở app grid | Pass | AOSP action mở `AppGridActivity`; UI hierarchy có app grid và 22 ứng dụng trên image này. |
| Layout app grid | Pass | Màn hình sạch có search field, tile Navigation, grid XML bốn cột, icon và label. |
| Tìm kiếm app | Pass | Gõ `Camera` làm field thành `Camera` và chỉ còn một kết quả `Camera`. |
| Chọn ứng dụng | Pass | Chọn Camera quay lại HOME hiện tại với target `com.android.camera2/com.android.camera.CameraLauncher`; Timber ghi target selection. |
| Chọn Navigation | Pass | Chọn tile Navigation quay lại HOME với target Maps Placeholder. |
| Back/close app grid | Pass | System Back quay lại HOME component; view Close cũng được nối vào cùng finish path. |
| Màn hình recents | Pass | Recents action mở `CarRecentsActivity`; list XML hiển thị `MySystemApp`, `Gallery`, `Remove` từng item và `Clear all`. |
| QuickStep contract | Pass khi build/tích hợp | Service compile với `ILauncherProxy` shared contract của image và khai báo `QUICKSTEP_SERVICE`. Chưa xác nhận callback trực tiếp từ SystemUI vì giới hạn SystemUI bên dưới. |
| TaskView host | Giới hạn bởi image | Launcher đã request `CarTaskViewController`, nhưng `dumpsys activity service com.android.car/.CarService` trả `CarSystemUIProxy registered: false`. Sau timeout hữu hạn, UI thật hiển thị `TaskView unavailable` và thông báo SystemUI chưa đăng ký proxy. |
| Retry/embedded task thực tế | Chưa pass được trên image này | TaskView service không thể gọi `onConnected` khi proxy chưa được đăng ký. Host và error path đã triển khai, nhưng không thể trung thực kết luận active TaskView hoạt động trên AVD hiện tại. |
| Media playback thực tế | Chưa thực hiện được | Image không có active media session/source. Đã kiểm thử empty state; cần media provider/session để kiểm tra callback playback. |

## Giới hạn TaskView

Image hiện tại chạy custom `custom-system-apps/Car-System-UI`. `SystemUIService`
của app này khởi động custom system-bar coordinator nhưng không tạo
`CarSystemUIInitializer` / `CarSystemUIProxyImpl` của AOSP. Vì vậy CarService
trả:

```text
CarSystemUIProxy registered: false
```

Đây là dependency cấp image mà CarLauncher AOSP ban đầu cũng cần. Car-Launcher
không sửa repository Car-System-UI của người dùng. Trên image có AOSP proxy
chuẩn, cần chạy lại case `ControlledRemoteCarTaskView` cho Navigation và một
ứng dụng được chọn.

## Hạn chế còn lại

- Chưa xác minh được rendering và đầy đủ lifecycle của TaskView cho tới khi AVD
  đăng ký `CarSystemUIProxy`.
- Chuyển media source, playback control, cập nhật artwork và seek cần media
  session provider; trong lần chạy này chỉ có empty state.
- APK standalone phụ thuộc platform jar từ build AOSP tương ứng trong workspace,
  không phải app generic cho thiết bị Play.
- AVD đã restart trong lúc kiểm thử, do đó initial-user notice của custom
  SystemUI có thể che launcher cho tới khi dismiss. Đây là hành vi bên ngoài
  launcher, không được tính là app crash.
