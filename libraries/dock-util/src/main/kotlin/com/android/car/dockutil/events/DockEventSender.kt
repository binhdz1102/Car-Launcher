package com.android.car.dockutil.events

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Sends the stock Dock broadcasts while keeping permission and extras centralized. */
class DockEventSender(
    private val context: Context,
) {
    fun sendLaunchEvent(task: ActivityManager.RunningTaskInfo) {
        task.componentName()?.let { send(DockEvent.LAUNCH, it) }
    }

    fun sendPinEvent(component: ComponentName) {
        send(DockEvent.PIN, component)
    }

    fun sendUnpinEvent(component: ComponentName) {
        send(DockEvent.UNPIN, component)
    }

    private fun send(
        event: DockEvent,
        component: ComponentName,
    ) {
        context.sendBroadcast(
            Intent(event.toString()).putExtra(EXTRA_COMPONENT, component),
            DockPermission.DOCK_RECEIVER_PERMISSION.toString(),
        )
    }

    private companion object {
        const val EXTRA_COMPONENT = "EXTRA_COMPONENT"
    }
}

private fun ActivityManager.RunningTaskInfo.componentName(): ComponentName? = baseActivity ?: baseIntent.component
