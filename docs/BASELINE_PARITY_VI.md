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
Evidence HOME mới nhất ở
`artifacts/parity/controlled-home-mediafix-20260814/run-20260814-161355`:
capture hợp lệ nhưng gate ảnh và manifest/resource vẫn fail. Setup Recents
deterministic ở
`artifacts/parity/recents-direct2-20260814/20260814-163744-baseline-recents`
được trả `INVALID` vì Maps stock vẫn top-resumed và log có fatal error.
