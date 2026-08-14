package com.android.car.docklib.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.car.docklib.DockInterface

/** Forwards package lifecycle changes to the Dock controller for the current user. */
class DockPackageChangeReceiver(
    private val dockController: DockInterface,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        val currentUserId = context.applicationInfo.uid / USER_ID_RANGE
        if (uid < 0 || uid / USER_ID_RANGE != currentUserId) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED -> if (!replacing) dockController.packageRemoved(packageName)
            Intent.ACTION_PACKAGE_ADDED -> if (!replacing) dockController.packageAdded(packageName)
            Intent.ACTION_PACKAGE_CHANGED -> {
                val state = context.packageManager.getApplicationEnabledSetting(packageName)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                ) {
                    dockController.packageRemoved(packageName)
                }
            }
        }
    }

    companion object {
        private const val API_33 = 33
        private const val USER_ID_RANGE = 100_000

        @JvmStatic
        fun registerReceiver(
            context: Context,
            dockController: DockInterface,
        ): DockPackageChangeReceiver {
            val receiver = DockPackageChangeReceiver(dockController)
            val filter =
                android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                }
            if (android.os.Build.VERSION.SDK_INT >= API_33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
