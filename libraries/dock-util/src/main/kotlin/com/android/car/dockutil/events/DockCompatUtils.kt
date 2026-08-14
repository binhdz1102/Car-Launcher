package com.android.car.dockutil.events

import android.content.Context
import com.android.car.dockutil.R

/** Resource-backed display routing used by the Dock event sender. */
object DockCompatUtils {
    @JvmStatic
    fun isDockSupportedOnDisplay(
        context: Context,
        displayId: Int,
    ): Boolean = context.resources.getIntArray(R.array.dock_supported_displays).contains(displayId)
}
