# Ma trận source-to-feature và test

`Launcher/` là source AOSP tham khảo và không được commit. Mỗi dòng có trạng
thái `ported`, `equivalent test` hoặc `open`; dòng `open` không phải evidence
nghiệm thu.

| Khu vực AOSP | Boundary candidate | Trạng thái/evidence |
| --- | --- | --- |
| HOME, map TaskView, display passenger | `feature:home:*`, `app` | Port lifecycle seam và Flow callback; AVD acceptance còn mở |
| Media/session và home card | `feature:media:*`, `feature:launcher:presentation` | Port state source/session/card; fixture matrix còn mở |
| Call, projection, assistive/weather | `feature:home:*`, `compat` | Port data contract; full UI/priority matrix còn mở |
| App Grid, paging, search, reorder, UXR | `feature:appgrid:*`, `libraries:appgrid` | Paging/UXR/persistence test pass; full visual/action matrix còn mở |
| ToS, mirroring và shortcut | `feature:appgrid:*`, `app` | Port intent/reducer seam; AVD matrix còn mở |
| Recents và QuickStep | `feature:recents:*`, `app` | Horizontal task flow/binder compile; multi-task AVD matrix còn mở |
| Calm Mode QC/activity | `feature:calmmode:*`, `app` | Gate/config/locale/provider test pass; AVD acceptance còn mở |
| WidgetHost và Date widget | `feature:widgets`, `app` | Rebind/time/locale reducer test pass; AVD acceptance còn mở |
| Dock library, event và sample host | `libraries:dock*`, `feature:dock:*` | Policy/codec/API seam/host build pass; AOSP API đầy đủ còn mở |
| Launcher common/public API | `libraries:launcher-common` | Compatibility lock ghi rõ partial-port |
| Fixture và instrumentation | `test-apps:fixture-app`, `app/src/androidTest` | Fixture và 6 contract test build; action scenario còn mở |

## Quy tắc boundary

- Module domain không import Android. Scope/dispatcher được inject; `Handler` chỉ
  nằm trong adapter callback Android bắt buộc.
- App launcher là composition root. Dock library không được đóng gói vào app
  khi host AOSP sở hữu Dock.
- App order đọc/ghi cả DataStore và `files/order.data`; Dock order dual-write
  DataStore cùng protobuf stock. Input hỏng fallback an toàn, không xóa file.
- Thay đổi package, component, action, authority, permission hoặc display routing
  phải được review contract; numeric resource ID có thể khác.

## Dòng acceptance còn mở

Các dòng còn lại cần chạy AVD API 37/user 10 sạch, API dump đầy đủ, evidence
parity ba lần và ngưỡng screenshot trong
[AVD_PARITY_GUIDE_VI.md](AVD_PARITY_GUIDE_VI.md). Không đổi sang `ported` chỉ vì
unit test hoặc token manifest pass.
