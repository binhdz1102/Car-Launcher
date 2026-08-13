# Hướng dẫn parity trên AVD

## Input đã khóa

Baseline chính thức là `Launcher/apk/CarLauncher.apk`, SHA-256
`17dbd56ce171ca7bcd06486cb7893da8232242661d3a3ff60b91f5a9cac9d3a5`; certificate
được ghi trong `baseline.lock.json`. Thiết bị là `emulator-5554`, API 37, user
10, snapshot `car_launcher_parity_ready`.

`Launcher/`, `platform-artifacts/` và `artifacts/` đều Git ignore. Lệnh dưới sẽ
từ chối suite destructive nếu chưa có snapshot:

```powershell
.\scripts\verify-baseline.ps1 -VerifyDevice -Serial emulator-5554
.\scripts\run-parity.ps1 -CandidateApk app\build\outputs\apk\release\app-release.apk
```

## Quy trình deterministic

1. Cài fixture APK và tạo/lưu snapshot. Nếu không save/load kiểm tra được thì
   dừng, không chạy suite destructive.
2. Restore snapshot và capture baseline bằng `adb install -r -d`.
3. Restore đúng snapshot rồi cài candidate release ký platform.
4. Chạy cùng sáu scenario: HOME, App Grid, Recents, Calm Mode, Widget Host,
   Map ToS.
5. So sánh screenshot và artifact manifest/routing thuộc launcher.
6. Fail thì restore snapshot. Pass thì cài lại candidate và smoke HOME cuối.

`run-parity.ps1` tự động hóa chuỗi trên và ghi thư mục run gồm `run.json`, một
folder mỗi scenario, badging, manifest XML tree, dumpsys activity/window/display,
UI hierarchy, screenshot và 3000 dòng logcat cuối.

## Quy tắc so sánh

Contract runner so component, permission, action thuộc launcher và resolution
user 10 cho HOME, App Grid, QuickStep. Resource ID và số dòng do build gán được
chuẩn hóa. Entry do support library sở hữu nhưng launcher không dùng sẽ được bỏ
qua và ghi trong migration matrix.

Screenshot gate dùng pixel tolerance RGB 16, SSIM global >= 0,98 và tối đa 2%
pixel khác biệt. Có thể mask clock/date, artwork, thumbnail hoặc vùng động đã
ghi rõ; không mask khác biệt layout/component. Ví dụ Calm Mode:

```powershell
python scripts\compare-parity.py `
  <baseline>\screen.png <candidate>\screen.png `
  --ignore-rect 760,0,420,100 `
  --ignore-rect 560,330,820,400
python scripts\compare-parity-contract.py <baseline> <candidate>
```

## Phạm vi hành vi bắt buộc

- HOME clean boot, TaskView map appear/update/remove/reconnect;
- App Grid dọc, recent/search/reorder, TOS, mirroring và UXR Park/Drive;
- media source, queue/history, call/projection/assistive card;
- Recents/QuickStep snapshot, open, remove, clear;
- Calm Mode QC, clock/date/nhiệt độ/media và control bar translucent;
- WidgetHost và Date widget;
- Dock order/event, secondary display routing và release install/restore.

Full run mới nhất là `artifacts/parity/run-20260813-204544`. Contract pass cả sáu
scenario; screenshot fail ở HOME, App Grid, Recents, Widget Host và Map ToS vì UI
replacement chưa pixel-identical. Không được gọi run này là nghiệm thu pass.
