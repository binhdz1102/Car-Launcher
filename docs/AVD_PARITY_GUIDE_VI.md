# Hướng dẫn parity trên AVD

## Đầu vào đã khóa

Baseline chính thức là `Launcher/apk/CarLauncher.apk`, SHA-256
`17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5`, certificate
được ghi trong `baseline.lock.json`. Thiết bị hỗ trợ là `emulator-5554`, API 37,
user 10 và snapshot `car_launcher_parity_ready`.

`Launcher/`, `platform-artifacts/` và `artifacts/` đều bị Git ignore. Lệnh parity
từ chối suite destructive nếu snapshot không tồn tại hoặc không restore được.

## Quy trình deterministic

1. Cài fixture APK, tạo và kiểm tra snapshot. Nếu save/load không thành công thì dừng.
2. Restore snapshot trước **từng** scenario, cài baseline bằng `adb install -r -d`.
3. Restore cùng snapshot trước **từng** scenario, cài candidate release platform-signed.
4. Chạy HOME, App Grid, Recents, Calm Mode, Widget Host và Map ToS.
5. So sánh screenshot, manifest/resource/API contract và topology task/display.
6. Khi fail hoặc invalid, runner restore snapshot; khi pass mới cài lại candidate và smoke HOME.

Mỗi `capture.json` ghi `preconditions`, `actions`, `assertions`, `taskTopology`,
`instrumentation`, `logWindow` và status. `PASS` chỉ hợp lệ khi component mong
đợi là top-resumed và log window riêng không có FATAL/ANR/SecurityException.
`INVALID` nghĩa là precondition sai hoặc baseline crash; runner không so sánh và
không được tính là parity pass.

## Quy tắc so sánh

Contract phải so toàn bộ merged manifest, component attributes, intent filters,
metadata, properties, queries, authorities, permissions và overlayable/public
resources. Resource ID và line number do build gán được chuẩn hóa; khác biệt
hành vi không được che bằng normalization.

Screenshot gate dùng RGB tolerance 16, SSIM tối thiểu 0,98 và tối đa 2% pixel
khác biệt. Chỉ được mask clock/date/artwork/thumbnail động đã ghi rõ; không mask
layout, TaskView hoặc component.

## Phạm vi hành vi bắt buộc

- HOME clean boot, TaskView map appear/update/remove/reconnect;
- App Grid paging, recent/search/reorder, TOS, mirroring và UXR Park/Drive;
- media source, queue/history, call/projection/assistive card;
- Recents/QuickStep snapshot, open, remove, swipe và clear;
- Calm Mode QC, clock/date/temperature/media và control bar translucent;
- WidgetHost/Date widget, Dock, secondary display và release install/restore;
- migration order từ baseline sang candidate và rollback ngược lại.

Full run cũ `artifacts/parity/run-20260813-204544` là bằng chứng fail trước khắc
phục, không được gọi là nghiệm thu pass.
