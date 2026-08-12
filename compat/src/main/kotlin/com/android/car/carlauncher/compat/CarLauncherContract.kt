package com.android.car.carlauncher.compat

/**
 * Stable action, component, authority, and permission strings shared with AAOS and SystemUI.
 * Keep values aligned with the baseline manifest; features must not create local copies.
 */
object CarLauncherContract {
    const val PACKAGE_NAME = "com.android.car.carlauncher"
    const val ACTION_APP_GRID = "$PACKAGE_NAME.ACTION_APP_GRID"
    const val ACTION_OPEN_RECENTS = "$PACKAGE_NAME.recents.OPEN_RECENT_TASK_ACTION"
    const val CALM_MODE_AUTHORITY = "$PACKAGE_NAME.calmmode"
    const val DOCK_SENDER_PERMISSION = "com.android.car.docklib.permission.BROADCAST_SENDER"
    const val DOCK_RECEIVER_PERMISSION = "com.android.car.docklib.permission.BROADCAST_RECEIVER"
}
