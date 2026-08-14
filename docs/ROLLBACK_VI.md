# Rollback và khôi phục

Rollback chỉ an toàn khi baseline lock và snapshot còn nguyên. Adapter order
dual-write để APK stock vẫn đọc được order sau khi candidate bị gỡ.

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
versionCode stock thấp hơn candidate. Kiểm tra lại `pm path` và HOME resolution.

## Khôi phục snapshot parity

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot
```

Lệnh trên chỉ restore snapshot đã tồn tại; nếu snapshot mất, cài fixture và
chạy `verify-fixtures.ps1` trước khi save snapshot mới. Không chạy suite phá
hủy khi snapshot chưa được save/load kiểm tra.

## Rollback source

Mỗi bước migration là commit build được. Xem và revert theo commit/tag:

```powershell
git log --oneline --decorate -20
git show audit-remediation-start-20260814
git revert <commit>
```

Không commit hoặc checkout đè cây `Launcher/`. Sau rollback source, chạy lại
ktlint/detekt/lint/unit test, verify certificate, build release và parity subset.

## Checklist

1. Dừng candidate và restore snapshot.
2. Cài baseline lock hoặc chuyển về tag migration cuối cùng đã chọn.
3. Resolve HOME, App Grid và QuickStep cho user 10.
4. Kiểm file app/dock order; dữ liệu corrupt phải fallback, không xóa file gốc.
5. Lưu artifact rollback và ghi rõ nguyên nhân.
