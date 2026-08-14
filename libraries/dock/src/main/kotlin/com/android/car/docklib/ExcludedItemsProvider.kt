package com.android.car.docklib

import android.content.ComponentName

/** Host-provided exclusion policy for Dock packages and activities. */
interface ExcludedItemsProvider {
    fun isPackageExcluded(pkg: String): Boolean

    fun isComponentExcluded(component: ComponentName): Boolean
}
