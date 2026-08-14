package com.android.car.dockutil.events

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Sends the AOSP Dock broadcast contract. The helper is deliberately side-effect-free when the
 * display is not configured for Dock, which prevents secondary-display launches from leaking to
 * the primary host.
 */
class DockEventSenderHelper(
    private val context: Context,
) {
    fun sendLaunchEvent(taskInfo: ActivityManager.RunningTaskInfo) {
        sendEventBroadcast(DockEvent.LAUNCH, taskInfo)
    }

    fun sendPinEvent(taskInfo: ActivityManager.RunningTaskInfo) {
        sendEventBroadcast(DockEvent.PIN, taskInfo)
    }

    fun sendPinEvent(componentName: ComponentName) {
        sendEventBroadcast(DockEvent.PIN, componentName)
    }

    fun sendUnpinEvent(taskInfo: ActivityManager.RunningTaskInfo) {
        sendEventBroadcast(DockEvent.UNPIN, taskInfo)
    }

    fun sendUnpinEvent(componentName: ComponentName) {
        sendEventBroadcast(DockEvent.UNPIN, componentName)
    }

    private fun sendEventBroadcast(
        event: DockEvent,
        taskInfo: ActivityManager.RunningTaskInfo,
    ) {
        if (!DockCompatUtils.isDockSupportedOnDisplay(context, taskInfo.displayIdCompat())) return
        taskInfo.componentName()?.let { sendEventBroadcast(event, it) }
    }

    private fun sendEventBroadcast(
        event: DockEvent,
        componentName: ComponentName,
    ) {
        if (!DockRuntimeFlags.isEnabled) return
        context.sendBroadcast(
            Intent(event.toString()).putExtra(EXTRA_COMPONENT, componentName),
            DockPermission.DOCK_RECEIVER_PERMISSION.toString(),
        )
    }

    private fun ActivityManager.RunningTaskInfo.componentName(): ComponentName? =
        baseActivity ?: baseIntent.component

    private fun ActivityManager.RunningTaskInfo.displayIdCompat(): Int =
        runCatching {
            ActivityManager.RunningTaskInfo::class.java.getField("displayId").getInt(this)
        }.getOrDefault(0)

    companion object {
        const val EXTRA_COMPONENT = "EXTRA_COMPONENT"
    }
}

/** Temporary source-compatible seam for the generated AOSP dock flag. */
object DockRuntimeFlags {
    @Volatile
    var isEnabled: Boolean = true
}
