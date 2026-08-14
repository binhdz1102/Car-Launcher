# Báo cáo test audit Car Launcher

Ngày ghi nhận: 2026-08-14. Baseline là
`Launcher/apk/CarLauncher.apk` đã khóa; artifact thô nằm trong thư mục bị
Git ignore.

## Kết luận

Migration hiện có release build ký platform và nhiều feature slice đã được
test. **Chưa được nghiệm thu thay thế launcher stock**. AVD run mới nhất dừng ở
baseline HOME vì cửa sổ launcher phát sinh ANR thật (`Input dispatching timed
out`). Runner fail-closed đã restore snapshot và không so candidate với
baseline không hợp lệ.

## Identity đã khóa

| Mục | Giá trị |
| --- | --- |
| Package | `com.android.car.carlauncher` |
| SHA-256 baseline | `17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5` |
| SHA-256 candidate release | `f8a083e01de5e24a40442cf2fb7a99702ad23dde9952718ce7cd71888b864bb8` |
| Certificate | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| Target | API 37 / user 10 / `emulator-5554` |

## Gate đã hoàn thành

- Kotlin compile, release APK và AndroidTest APK: **PASS**
- Test codec order AppGrid/Dock round-trip và fallback truncated/corrupt:
  **PASS**
- Test Dock policy và sample host: **PASS**
- ktlint module thay đổi và verify release: **PASS**
- Resource overlayable contract: **PASS** (354 + 120 item)
- Nghiệm thu connected AVD parity: **CHƯA CHẠY XONG**

## Bằng chứng AVD

Run mới nhất là `artifacts/parity/audit-run-home/run-20260814-105014/run.json`.
Baseline HOME bị đánh dấu `INVALID` với lý do log có fatal/ANR/security; logcat
ghi nhận launcher ANR. Runner đã kiểm fixture, task topology và instrumentation
6 test trước khi dừng. Điều này chứng minh guard hoạt động, không chứng minh
parity ảnh hay hành vi.

Run lịch sử `artifacts/parity/run-20260813-204544` chỉ dùng làm bằng chứng fail
trước remediation, không dùng làm release gate.

## Gate phát hành còn lại

Sau khi sửa nguyên nhân ANR/setup baseline, chạy full matrix sáu scenario ba lần
từ snapshot sạch. Mọi capture phải `PASS`, contract diff bằng 0 ngoài version/
build được cho phép, tất cả case TaskView/UXR/media/Recents/WidgetHost/Dock và
rollback dữ liệu phải chạy, screenshot đạt SSIM >= 0.98 và pixel khác biệt <= 2%.
Chỉ để candidate trên AVD sau smoke cuối pass.
