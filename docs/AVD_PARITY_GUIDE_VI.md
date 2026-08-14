# Hướng dẫn parity trên AVD

## Đầu vào đã khóa

Baseline chính thức là `Launcher/apk/CarLauncher.apk`, SHA-256
`17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5`. Mục tiêu
là AVD API 37/user 10 trên `emulator-5554`; snapshot khôi phục là
`car_launcher_parity_ready`.

## Quy trình bắt buộc

1. Kiểm baseline, platform artifact, trạng thái boot và fixture APK.
2. Save rồi load snapshot. Nếu không save/load được thì dừng, không chạy suite
   có thay đổi trạng thái.
3. Restore snapshot trước từng scenario và cài baseline bằng
   `adb install --no-streaming -r -d --user 10`.
4. Restore lại, cài candidate release ký platform và chạy đúng scenario cùng
   instrumentation.
5. So sánh screenshot, UI hierarchy, topology task/display và toàn bộ contract
   manifest, permission, component, query, routing, overlayable.
6. Restore khi có lỗi. Khi pass, cài lại release và smoke HOME cuối.

Lệnh chuẩn:

```powershell
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk `
  -InstrumentationApk app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Runner bao phủ HOME, App Grid, Recents, Calm Mode, WidgetHost và Map ToS. Mỗi
`capture.json` ghi `preconditions`, `actions`, `assertions`, `taskTopology`,
`instrumentation` và `logWindow`. `PASS` yêu cầu foreground đúng, fixture sẵn
sàng, không có map task trùng, không fatal/ANR/`SecurityException` và
instrumentation pass. Thiếu fixture, foreground sai hoặc topology sai là
`INVALID`, không được đem so sánh.

## Gate ảnh và contract

Comparator dùng RGB tolerance 16, SSIM >= 0.98 và pixel khác biệt <= 2%. Chỉ
được mask clock/date, artwork và thumbnail động; không mask layout, TaskView
hay component. Contract chỉ normalize line number, numeric resource ID và
version build.

Resource lock yêu cầu 354 overlayable app và 120 overlayable AppGrid. Phải chạy
API-compat/resource verification trước AVD suite. Không gọi run `INVALID` là
pass. Evidence hiện tại ở
`artifacts/parity/audit-run-home/run-20260814-105014/run.json`; run này invalid
vì baseline HOME phát sinh ANR trên AVD.
