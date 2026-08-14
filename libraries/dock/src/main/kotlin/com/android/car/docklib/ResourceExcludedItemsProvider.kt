package com.android.car.docklib

import android.content.ComponentName
import android.content.Context

/** Reads the AOSP Dock exclusion arrays from the host's resources. */
class ResourceExcludedItemsProvider(
    context: Context,
) : ExcludedItemsProvider {
    private val resources = context.resources
    private val packageName = context.packageName

    private val excludedPackages =
        stringArray("config_packagesExcludedFromDock").toSet()
    private val excludedComponents =
        stringArray("config_componentsExcludedFromDock")
            .mapNotNull(ComponentName::unflattenFromString)
            .toSet()

    private fun stringArray(name: String): Array<String> {
        val resourceId = resources.getIdentifier(name, "array", packageName)
        return resourceId.takeIf { it != 0 }?.let(resources::getStringArray) ?: emptyArray()
    }

    override fun isPackageExcluded(pkg: String): Boolean = pkg in excludedPackages

    override fun isComponentExcluded(component: ComponentName): Boolean = component in excludedComponents
}
