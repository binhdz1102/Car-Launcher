# Chức năng Car Launcher

Tài liệu này mô tả contract và phần triển khai hiện tại của package
`com.android.car.carlauncher`. Đây là bản migration đang audit, chưa phải kết
luận parity hoàn tất.

## HOME và CarService

`CarLauncher` xử lý HOME/SECONDARY_HOME và các intent launcher được giữ trong
manifest stock. HOME hiển thị card media và pane ứng dụng nhúng. Kết nối
CarService phát state bằng Flow, có reconnect và không tạo thread quản lý thủ
công ở feature.

## Media và home cards

Repository kết hợp CarMediaManager, MediaSession và MediaController để phát
title, artist, artwork, position/duration, previous, play/pause, next, seek,
source switch và launch Media Center. Call, projection, assistive và priority
state có data contract; fixture/AVD matrix đầy đủ vẫn cần nghiệm thu.

## Navigation và TaskView

Map intent được resolve theo user hiện tại rồi đưa vào Controlled TaskView. Host
theo dõi task appear/info/vanish, bounds, reconnect và release. `MapTosActivity`
là fallback ToS và có đường vào App Grid. Các case task removal/update, PIP,
headless-user và display passenger phải được chứng minh bằng parity runner.

## App Grid và an toàn khi lái xe

App Grid có action stock, discovery LauncherApps, media service tile, search,
recent/reorder, reset A-Z, shortcut pin/unpin/force-stop/app-info và persistence.
Car UX restriction ẩn search/reorder khi Drive và disable app non-DO; media và
app DO vẫn được phép. `QUERY_ALL_PACKAGES` được giữ theo AOSP và suppression
lint cục bộ có giải thích.

## Recents, QuickStep, Calm Mode và widget

Recents dùng layout ngang, task snapshot, open/remove/clear và QuickStep binder.
Calm Mode có QC provider, feature/resource gate, nhiệt độ C/F, media text,
animation và control-bar routing. WidgetHost giữ ID qua stop/start/rebind; Date
widget cập nhật theo time/date/timezone/locale.

## Dock và compatibility

Dock library cung cấp item model, policy pin/unpin/dynamic replacement, media và
task helper, broadcast receiver, XML host/view adapter và sample host. Launcher
app không đóng gói Dock feature khi SystemUI/host AOSP sở hữu nó. API lock còn
ghi `partial-port` cho phần AOSP chưa port.

## Điều kiện nghiệm thu

Mỗi chức năng phải có unit/equivalent test, AVD action/assertion và evidence
không ANR. Xem [MIGRATION_MATRIX_VI.md](MIGRATION_MATRIX_VI.md) và
[AVD_PARITY_GUIDE_VI.md](AVD_PARITY_GUIDE_VI.md).
