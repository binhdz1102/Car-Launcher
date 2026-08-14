# Báo cáo test audit Car Launcher

Ngày bằng chứng: 2026-08-14. Baseline chính thức là
`Launcher/apk/CarLauncher.apk` (bị Git ignore); artifact AVD thô cũng bị ignore
và được tham chiếu bằng đường dẫn.

## Kết luận

Migration hiện có release ký platform, static gate trung thực và parity runner
fail-closed. **Chưa được nghiệm thu để thay launcher stock**: UI HOME của
candidate chưa trùng layout media/home-card AOSP, resource merged còn thiếu và
Recents baseline trên AVD vẫn quay về Maps kèm fatal error.

## Identity đã khóa

| Mục | Giá trị |
| --- | --- |
| Package | `com.android.car.carlauncher` |
| SHA-256 baseline | `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` |
| SHA-256 candidate release | `2bd26ef1b499e70c8f49a9a6b04b089c190dd36ea6cc75f9c3d07ee0419dc335` |
| Certificate platform | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| Mục tiêu | API 37/Baklava, user 10, `emulator-5554` |

## Gate đã hoàn thành

- `ktlintCheck`, `detekt`, `lintDebug` mục tiêu, architecture/source/resource,
  toàn bộ `testDebugUnitTest`, build debug/release và APK AndroidTest:
  **PASS**.
- Codec order AppGrid/Dock, fallback dữ liệu hỏng, chọn media controller và
  fixture contract: **PASS**.
- Overlayable source contract: **PASS** (354 item app + 120 item AppGrid).
- Kiểm certificate release: **PASS**.
- Nghiệm thu full parity: **FAIL / CÒN MỞ**; không ghi nhận release pass.

## Bằng chứng AVD

1. `artifacts/parity/controlled-home-mediafix-20260814/run-20260814-161355`:
   baseline/candidate capture đều `PASS`, candidate chọn đúng `Fixture Drive`,
   topology/log hợp lệ; nhưng screenshot SSIM `0.8538034994`, khác `8.7308578%`
   (**FAIL**). Contract cũng **FAIL** do application name/`testOnly` thừa và
   3.884 token resource chỉ có ở baseline / 199 token chỉ có ở candidate.
2. `artifacts/parity/remediation-matrix-20260814b/run-20260814-162034`:
   HOME và App Grid baseline pass; Recents baseline bị runner trả `INVALID` vì
   Maps lên top-resumed và log có hai fatal exception. Candidate không được so
   với baseline không hợp lệ.
3. `artifacts/parity/recents-direct2-20260814/20260814-163744-baseline-recents`:
   đã seed task utility/media/map và ghi lại trong actions; stock Recents vẫn
   quay về Maps kèm fatal, nên đây là baseline invalid chứ không phải pass.

Run lịch sử `artifacts/parity/run-20260813-204544` chỉ là bằng chứng fail trước
remediation.

## Điều kiện nghiệm thu còn mở

- Port phần AOSP còn thiếu cho media/home-card, App Grid, Recents/QuickStep,
  Calm/WidgetHost, Dock và public library; mỗi symbol phải có test tương đương
  hoặc lý do N/A được phê duyệt.
- Khớp merged manifest/resource (kể cả resource Car UI/SystemUI truyền qua) và
  bỏ application attribute ngoài contract.
- Ổn định setup Recents baseline, sau đó chạy full matrix ba lần liên tiếp từ
  `car_launcher_parity_ready` với instrumentation.
- Chỉ bàn giao khi capture `PASS`, không ANR/fatal/SecurityException, topology
  tương đương, SSIM >= 0.98 và pixel khác biệt <= 2%.
