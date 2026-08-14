package com.android.car.docklib.events

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.car.docklib.DockInterface
import com.android.car.dockutil.events.DockEvent
import com.android.car.dockutil.events.DockEventSenderHelper
import com.android.car.dockutil.events.DockPermission

/** Receives the signature-protected Dock pin/launch event contract. */
class DockEventsReceiver(
    private val dockController: DockInterface,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val component =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    DockEventSenderHelper.EXTRA_COMPONENT,
                    ComponentName::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<ComponentName>(DockEventSenderHelper.EXTRA_COMPONENT)
            }
        val event = DockEvent.toDockEvent(intent.action) ?: return
        val actualComponent = component ?: return
        when (event) {
            DockEvent.LAUNCH -> dockController.appLaunched(actualComponent)
            DockEvent.PIN -> dockController.appPinned(actualComponent)
            DockEvent.UNPIN -> dockController.appUnpinned(actualComponent)
        }
    }

    companion object {
        @JvmStatic
        fun registerDockReceiver(
            context: Context,
            dockController: DockInterface,
        ): DockEventsReceiver {
            val receiver = DockEventsReceiver(dockController)
            val filter =
                IntentFilter().apply {
                    addAction(DockEvent.LAUNCH.toString())
                    addAction(DockEvent.PIN.toString())
                    addAction(DockEvent.UNPIN.toString())
                }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(
                    receiver,
                    filter,
                    DockPermission.DOCK_RECEIVER_PERMISSION.toString(),
                    null,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(
                    receiver,
                    filter,
                    DockPermission.DOCK_RECEIVER_PERMISSION.toString(),
                    null,
                )
            }
            return receiver
        }
    }
}
