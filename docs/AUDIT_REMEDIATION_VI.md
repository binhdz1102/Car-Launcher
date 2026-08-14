# Trạng thái khắc phục audit Car Launcher

Tài liệu này là tóm tắt phát hành cho migration AOSP. `Launcher/` và các
artifact platform/parity sinh ra chỉ dùng làm tham chiếu và vẫn bị Git ignore.

## Đầu vào đã khóa

- Baseline: `Launcher/apk/CarLauncher.apk`
- SHA-256 baseline: `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5`
- SHA-256 certificate platform: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`
- Mục tiêu: AVD API 37, user 10, serial `emulator-5554`
- Snapshot khôi phục: `car_launcher_parity_ready`

## Chuỗi commit khôi phục

Chuỗi bắt đầu tại tag `audit-remediation-start-20260814` trên `main`:

1. `bf18a04` sửa quality gate và suppression lint có giải thích
2. `554889b` parity capture/runner fail-closed và snapshot guard
3. `4ec7636` contract manifest/resource và 474 overlayable
4. `25da7c9` seam API AppGrid, Dock và common
5. `99b8b11` flags injectable, application scope và callback Flow
6. `0f7e9fb` lifecycle HOME/TaskView và host display phụ
7. `3d4b3c5` state media, call, projection và assistive card
8. `6de6366` paging, UXR, shortcut và persistence App Grid
9. `e64c3e8` luồng task Recents/QuickStep
10. `9425eec` Calm Mode, WidgetHost và Date widget
11. `d2a9800` behavior Dock và sample host
12. `4085451` codec order rollback-safe và parity harness đầy đủ
13. `1ba8712` static gate cuối, lint permission platform và media cleanup
14. `8d31380` boundary module sạch, source lock và persistence atomic
15. `d3278b9` page padding AppGrid và hierarchy resource/accessibility AOSP
16. `736de9c` precondition UI/task fail-closed, seed fixture và contract diff
17. `7cdea62` lifecycle callback TaskView trên main và chọn media session
18. `b14eca0` manifest compatibility endpoint và cấu hình release API 37

API/library lock vẫn ghi `partial-port` khi AOSP chưa được port đủ. Vì vậy
seam pass không bị hiểu nhầm là parity hành vi hoàn chỉnh.

## Bằng chứng hiện tại

- Full Gradle static/unit/build gate (gồm `verifyArchitecture` và
  `:app:assembleAndroidTest`): **PASS**
- Unit test AppGrid/Dock/media/data và ktlint/detekt: **PASS**
- Resource contract: **PASS** (354 overlayable app + 120 AppGrid)
- Source-tree/boundary check: **PASS**; API AppGrid/Dock vẫn `partial-port`,
  nên chưa phải acceptance.
- Verify release APK: **PASS**; SHA-256
  `2bd26ef1b499e70c8f49a9a6b04b089c190dd36ea6cc75f9c3d07ee0419dc335`
- Nghiệm thu full AVD parity: **CHƯA ĐẠT**

Run HOME hợp lệ mới nhất là
`artifacts/parity/controlled-home-mediafix-20260814/run-20260814-161355`:
capture và topology/log đều `PASS`, candidate chọn được session Fixture Drive,
nhưng SSIM chỉ `0.8538034994`, pixel khác `8.7308578%` và contract
manifest/resource vẫn fail. Khác biệt còn lại là layout media/card HOME.

Run Recents
`artifacts/parity/recents-direct2-20260814/20260814-163744-baseline-recents`
đã seed task fixture xác định, sau đó trả `INVALID` đúng yêu cầu vì stock
Recents đưa Maps lên top-resumed và log có hai fatal exception.
`artifacts/parity/remediation-matrix-20260814b/run-20260814-162034` cũng dừng ở
precondition baseline tương tự. Đây là run invalid, không phải parity pass.
`artifacts/parity/run-20260813-204544` chỉ là bằng chứng fail lịch sử.

## Điều kiện nghiệm thu còn mở

Phải chứng minh ba lần liên tiếp từ snapshot sạch:

1. Capture baseline/candidate đều `PASS`, không ANR, fatal exception hay
   `SecurityException`; thiếu fixture, foreground hoặc topology task/display sai
   phải trả `INVALID`.
2. Manifest merged, queries, permission, component, intent filter, metadata,
   routing và resource/overlayable không có diff ngoài danh sách cho phép.
3. API dump AppGrid, Dock và launcher-common được map với symbol public AOSP;
   phần chưa port phải có trạng thái port hoặc N/A kèm lý do.
4. HOME/TaskView, media/call/projection, App Grid, Recents/QuickStep,
   Calm/WidgetHost, Dock, display phụ và rollback dữ liệu hai chiều đều pass
   trên API 37/user 10.
5. Mỗi screenshot đạt SSIM >= 0.98 và pixel khác biệt <= 2%, chỉ mask
   clock/date/artwork/thumbnail động đã ghi rõ; không mask layout hay TaskView.

Xem [BUILD_INSTALL_VI.md](BUILD_INSTALL_VI.md),
[AVD_PARITY_GUIDE_VI.md](AVD_PARITY_GUIDE_VI.md) và
[ROLLBACK_VI.md](ROLLBACK_VI.md) để chạy lại.
