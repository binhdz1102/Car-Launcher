# APK baseline và quy trình parity

APK tham chiếu cục bộ là `Launcher/apk/CarLauncher.apk`. File này được Git ignore có chủ đích.
`baseline.lock.json` khóa danh tính APK, certificate platform, image đích và Android user.

Chạy kiểm tra danh tính tĩnh trước khi thu thập bằng chứng:

```powershell
.\scripts\verify-baseline.ps1 -VerifyDevice
```

Thu thập cùng một trạng thái cho APK gốc và APK cần kiểm tra. `-Install` thay đổi AVD đang kết nối;
cài APK gốc dùng thêm `-d` vì versionCode là 37.

```powershell
.\scripts\capture-parity.ps1 -Label baseline -ApkPath Launcher\apk\CarLauncher.apk -Install -Scenario home -LaunchScenario
.\scripts\capture-parity.ps1 -Label candidate -ApkPath app\build\outputs\apk\release\app-release.apk -Install -Scenario home -LaunchScenario
```

Script lưu metadata APK, component resolution, package/activity/window/display dump, UI XML,
screenshot và logcat vào `artifacts/parity/` đã được ignore. Hai lần capture phải chạy từ cùng AVD
snapshot đã restore, cùng fixture state và user 10.

So sánh screenshot sau khi mask clock, date, artwork và thumbnail được phép thay đổi:

```powershell
python scripts\compare-parity.py <baseline-screen.png> <candidate-screen.png> --ignore-rect 0,0,400,120
python scripts\compare-parity-contract.py <baseline-artifact-dir> <candidate-artifact-dir>
```

Gate mặc định yêu cầu global RGB SSIM tối thiểu 0,98 và không quá 2% pixel được so sánh vượt quá
chênh lệch 16 ở bất kỳ kênh màu nào.

So sánh contract sẽ chặn mọi thay đổi component/permission/intent-filter trong manifest và thay đổi
phân giải HOME, App Grid hoặc QuickStep cho user 10. Resource ID được chuẩn hóa có chủ ý vì build
candidate tự gán ID.

`run-parity.ps1` từ chối cài bất kỳ APK nào trước khi AVD có snapshot được chỉ định. Hãy tạo và
kiểm tra snapshot sau khi cài fixture test, rồi chạy ma trận baseline/candidate:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot
.\scripts\run-parity.ps1 -CandidateApk app\build\outputs\apk\release\app-release.apk
```

Runner restore snapshot trước mỗi nhãn, chạy HOME, App Grid, Recents, Calm Mode, Widget Host và
Map ToS với cùng input của user 10, so sánh từng cặp, restore khi fail, và chỉ giữ candidate sau
final HOME smoke pass.

Dùng `-Scenarios home` cho smoke nhanh của harness; nghiệm thu release luôn dùng matrix mặc định.
