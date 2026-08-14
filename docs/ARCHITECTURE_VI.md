# Kiến trúc Car Launcher

Tài liệu mô tả phần đã migrate; cây `Launcher/` bị Git ignore và chỉ dùng làm
tham chiếu. Target chính thức là AVD API 37 trong `baseline.lock.json`.

## Ranh giới module

```text
app
├─ entry point, manifest component và XML screen
├─ core/{common,model,platform,ui,testing}
├─ feature/{launcher,home,media,appgrid,recents,calmmode,dock}/{domain,data,presentation}
├─ feature/widgets
├─ libraries/{appgrid,dock,dock-util,launcher-common}
├─ compat
└─ test-apps/fixture-app và test-apps/dock-host
```

`domain` giữ model bất biến và repository contract. `data` cô lập Android,
CarService, LauncherApps, MediaSession, TaskManager, DataStore và protobuf.
`presentation` giữ ViewModel, `StateFlow`/`SharedFlow`, activity/fragment và
adapter XML. `app` là composition root và giữ package/component/resource
contract.

Quy tắc dependency là `presentation -> domain`, `data -> domain` và app lắp
platform implementation qua Hilt. Domain không import Android.

## State và concurrency

Callback Android được chuyển thành `callbackFlow` ở biên, sau đó repository và
ViewModel phát `StateFlow`. UI collect theo lifecycle; coroutine bị hủy theo
structured concurrency. `Handler` chỉ còn trong adapter callback bắt buộc của
Android, không dùng để điều phối state ứng dụng.

```text
CarService/SystemUI/LauncherApps/MediaSession
        -> callbackFlow adapter
        -> repository state + persistence
        -> ViewModel combine/stateIn
        -> XML collection và SharedFlow effect
```

## HOME, TaskView và persistence

`CarLauncher` sở hữu HOME window. Controlled TaskView chỉ tạo sau khi
CarService/SystemUI sẵn sàng; callback task được chuẩn hóa thành event Flow,
release khi stop/disconnect và tạo lại khi reconnect. Display phụ dùng App Grid
trong HOME host thay vì tạo task launcher thứ hai.

App order dual-write DataStore cùng `files/order.data`; Dock order dual-write
DataStore cùng protobuf stock. Decoder hỏng fallback an toàn và không xóa file
nguồn để rollback về APK stock không mất order.

## Trạng thái audit

Các boundary trên đã có unit/static test tương ứng, nhưng AVD parity đầy đủ,
API dump public hoàn chỉnh và các case TaskView/media/Recents/Dock vẫn đang mở.
Xem [AUDIT_REMEDIATION_VI.md](AUDIT_REMEDIATION_VI.md).
