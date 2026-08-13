# Rollback và khôi phục

Rollback an toàn khi có AVD snapshot và baseline đã khóa. Adapter persistence
dual-write thứ tự app/dock nên đổi APK không chủ động xóa state người dùng.

## Khôi phục APK stock

```powershell
.\scripts\install-avd.ps1 `
  -Serial emulator-5554 -UserId 10 -RestoreBaseline
adb -s emulator-5554 shell am force-stop com.android.car.carlauncher
adb -s emulator-5554 shell am start --user 10 -W `
  -a android.intent.action.MAIN -c android.intent.category.HOME `
  -n com.android.car.carlauncher/.CarLauncher
```

Installer kiểm SHA-256/certificate baseline rồi dùng `install -r -d` vì
versionCode stock 37 thấp hơn candidate 1000. Kiểm tra `pm path` quay lại
`/system/priv-app` sau khi gỡ update nếu package manager expose fallback đó.

## Khôi phục snapshot

Sau parity run fail hoặc bị ngắt:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot
```

Lệnh restore snapshot `car_launcher_parity_ready` đang có mà không thay snapshot.
Nếu snapshot mất, chỉ tạo lại sau khi cài fixture và chạy `verify-fixtures.ps1`.
Không chạy comparison destructive khi snapshot chưa được kiểm tra.

## Rollback source

Mỗi bước migrate là commit build được độc lập. Xem và revert theo commit/tag,
không rewrite history:

```powershell
git log --oneline --decorate -20
git show pre-full-migration
git revert <commit>
```

Source tham khảo `Launcher/` bị ignore và không nằm trong commit. Nếu rollback
làm đổi version hoặc manifest, phải build lại, chạy quality gate, kiểm
certificate release rồi chạy lại matrix AVD.

## Checklist khôi phục

1. Dừng candidate và restore snapshot.
2. Cài APK stock đã khóa hoặc chuyển về tag migration tốt gần nhất.
3. Resolve HOME, App Grid, QuickStep cho user 10.
4. Kiểm app/dock order đọc được và không có FATAL/security error.
5. Lưu artifact parity mới và ghi lý do rollback.
