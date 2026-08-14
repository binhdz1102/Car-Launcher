# APK baseline và quy trình parity

APK tham chiếu là `Launcher/apk/CarLauncher.apk` và bị Git ignore.
`baseline.lock.json` khóa SHA-256, certificate, package/version, image mục tiêu
và user.

Kiểm đầu vào trước:

```powershell
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\verify-platform-artifacts.ps1 -VerifyDevice -Serial emulator-5554
```

Full runner yêu cầu fixture deterministic và AndroidTest APK:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot -InstallFixtures `
  -FixtureApk test-apps\fixture-app\build\outputs\apk\debug\fixture-app-debug.apk
.\scripts\run-parity.ps1 `
  -CandidateApk app\build\outputs\apk\release\app-release.apk `
  -InstrumentationApk app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Runner restore `car_launcher_parity_ready` trước từng label/scenario, cài
baseline có downgrade, cài candidate release, xóa toàn bộ logcat, kiểm
fixture/foreground/topology task, chạy instrumentation, lưu UI XML/screenshot
và restore snapshot khi fail. Capture thiếu precondition hợp lệ là `INVALID`,
không phải pass.

Gate ảnh dùng RGB tolerance 16, SSIM >= 0.98 và pixel khác biệt <= 2%. Chỉ được
mask clock/date/artwork/thumbnail đã ghi rõ. Gate contract so sánh component,
permission, intent filter, metadata, query, route và resource name; chỉ
normalize version build, numeric resource ID và line number.

Smoke harness không nghiệm thu dùng `-Scenarios home -SkipInstrumentation`.
Audit mới nhất ở `artifacts/parity/audit-run-home/run-20260814-105014/run.json`
invalid vì baseline HOME phát sinh ANR trên AVD.
