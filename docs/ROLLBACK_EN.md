# Rollback and recovery

Rollback is safe only when the AVD snapshot and the locked baseline are
available. Persistence adapters dual-write app and dock order so reverting the
APK does not intentionally erase user state.

## Restore the stock APK

```powershell
.\scripts\install-avd.ps1 `
  -Serial emulator-5554 -UserId 10 -RestoreBaseline
adb -s emulator-5554 shell am force-stop com.android.car.carlauncher
adb -s emulator-5554 shell am start --user 10 -W `
  -a android.intent.action.MAIN -c android.intent.category.HOME `
  -n com.android.car.carlauncher/.CarLauncher
```

The installer verifies the baseline SHA-256 and certificate, then uses
`install -r -d` because stock versionCode 37 is lower than the candidate's
versionCode 1000. Confirm `pm path` points back to `/system/priv-app` after
removing the update if the package manager exposes that fallback.

## Snapshot recovery

After a failed or interrupted parity run:

```powershell
.\scripts\run-parity.ps1 -CreateSnapshot
```

This restores the existing `car_launcher_parity_ready` snapshot without replacing
it. If the snapshot is missing, recreate it only after reinstalling fixtures and
running `verify-fixtures.ps1`. Never run a destructive comparison with an
unverified snapshot.

## Source rollback

Each migration step is an independent buildable commit. Inspect and revert by
commit/tag rather than rewriting history:

```powershell
git log --oneline --decorate -20
git show pre-full-migration
git revert <commit>
```

The reference source under `Launcher/` is ignored and is not part of any commit.
If a source rollback changes the APK version or manifest, rebuild, run the static
quality gate, verify the release certificate, then repeat the AVD matrix.

## Recovery checklist

1. Stop the candidate and restore the named snapshot.
2. Install the locked stock APK or switch to the last known-good migration tag.
3. Resolve HOME, App Grid and QuickStep for user 10.
4. Confirm app/dock order files are readable and no FATAL/security error exists.
5. Save a new parity artifact directory and record the rollback reason.
