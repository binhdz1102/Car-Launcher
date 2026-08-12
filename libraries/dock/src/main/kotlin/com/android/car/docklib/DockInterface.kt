package com.android.car.docklib

import android.content.ComponentName
import com.android.car.docklib.data.DockItemId
import java.util.UUID

/** Stable public Dock callback contract used by launcher and sample hosts. */
interface DockInterface {
    fun appPinned(componentName: ComponentName)

    fun appPinned(
        componentName: ComponentName,
        index: Int,
    )

    fun appPinned(
        @DockItemId id: UUID,
    )

    fun appUnpinned(componentName: ComponentName)

    fun appUnpinned(
        @DockItemId id: UUID,
    )

    fun appLaunched(componentName: ComponentName)

    fun packageRemoved(packageName: String)

    fun packageAdded(packageName: String)

    fun launchApp(
        componentName: ComponentName,
        isMediaApp: Boolean,
    )

    fun getIconColorWithScrim(componentName: ComponentName): Int

    fun getMediaServiceComponents(): Set<ComponentName>
}
