package com.android.car.dockutil.events

/** Permission strings declared by Dock sender and receiver components. */
enum class DockPermission(
    private val value: String,
) {
    DOCK_SENDER_PERMISSION("com.android.car.docklib.permission.BROADCAST_SENDER"),
    DOCK_RECEIVER_PERMISSION("com.android.car.docklib.permission.BROADCAST_RECEIVER"),
    ;

    override fun toString(): String = value
}
