# Ma trận migrate

`Launcher/` chỉ dùng làm tham khảo và không bao giờ được commit. Bảng dưới ánh
xạ khu vực chức năng sang boundary của replacement và bằng chứng trên AVD API 37.

| Khu vực source tham khảo | Module thay thế | Contract/bằng chứng chính |
| --- | --- | --- |
| `CarLauncher` HOME, map và task card | `feature:home:*`, `core:platform`, `app` | HOME resolution, clean boot, Maps Placeholder TaskView và reconnect |
| Media card/source/session | `feature:media:*`, `feature:home:presentation` | fixture media browser, playback/source action và collect state HOME |
| Call, projection, assistive card | `feature:home:*`, `compat` | contract component/permission và empty-card trên AVD |
| App Grid, search, shortcut, reorder | `feature:appgrid:*`, `libraries:appgrid` | action App Grid, search, drag persistence, reset và UXR khi lái |
| ToS / map fallback | `app` (`MapTosActivity`) | explicit activity và Apps route sang App Grid |
| Recents và QuickStep | `feature:recents:*`, `core:platform`, `app` | app-switch, snapshot, open/remove/clear và QuickStep resolution |
| Calm Mode fragment/activity/QC | `feature:calmmode:*`, `app` | provider, nhiệt độ, translucent window và masked SSIM 1.0 |
| Date widget / WidgetHost | `feature:widgets`, `app` | Hilt host và Date widget render; strict visual vẫn khác |
| Dock library, event và order | `feature:dock:*`, `libraries:dock*`, `libraries:launcher-common` | protobuf codec, DataStore dual-write và event contract |
| Car callback và SystemUI bridge | `core:platform`, `core:common`, `compat` | adapter callbackFlow, reconnect state và manifest permission |
| Sample/fixture host | `test-apps:fixture` | input deterministic cho media, map, widget và launcher |

## Quyết định tương thích

- Giữ nguyên package `com.android.car.carlauncher`, tên component công khai,
  action, authority và launcher-owned permission.
- Resource ID không cần giống số; runner chuẩn hóa ID do build gán và so sánh
  token thuộc launcher.
- Component do support library merge vào APK baseline nhưng SystemUI AVD không
  resolve sẽ không được copy; boundary được ghi rõ trong contract harness.
- Persistence được dual-write trong giai đoạn migrate để rollback đọc được file
  order gốc.

## Parity còn lại

Full run chứng minh manifest/routing tương thích ở cả sáu scenario, nhưng strict
screenshot gate chưa đạt cho HOME, App Grid, Recents, Widget Host và Map ToS. Các
surface này còn XML composition đơn giản hơn styling DEWD/card/grid của stock.
Không hạ ngưỡng SSIM để che phần việc visual này.
