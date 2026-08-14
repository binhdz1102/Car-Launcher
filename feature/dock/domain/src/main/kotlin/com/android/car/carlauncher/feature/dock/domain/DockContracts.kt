package com.android.car.carlauncher.feature.dock.domain

import com.android.car.carlauncher.core.model.LauncherComponent
import kotlinx.coroutines.flow.Flow

data class DockItem(
    val component: LauncherComponent,
    val position: Int,
    val isMediaApp: Boolean,
    val kind: DockItemKind = if (isMediaApp) DockItemKind.MEDIA else DockItemKind.STATIC,
    val isDistractionOptimized: Boolean = true,
    val isExcited: Boolean = false,
    val isUpdating: Boolean = false,
)

enum class DockItemKind {
    STATIC,
    DYNAMIC,
    RECENT,
    MEDIA,
}

/** Pure ordering and UXR rules shared by the Dock data and host implementations. */
object DockPolicy {
    fun pin(
        current: List<LauncherComponent>,
        component: LauncherComponent,
        position: Int,
    ): List<LauncherComponent> {
        require(position >= 0) { "Dock position must be non-negative" }
        val next = current.filterNot { it.flattened == component.flattened }.toMutableList()
        next.add(position.coerceAtMost(next.size), component)
        return next
    }

    fun unpin(
        current: List<LauncherComponent>,
        component: LauncherComponent,
    ): List<LauncherComponent> = current.filterNot { it.flattened == component.flattened }

    fun dynamicReplacementIndex(
        items: List<DockItem>,
        component: LauncherComponent,
        maxItems: Int,
    ): Int? {
        require(maxItems > 0) { "maxItems must be positive" }
        items
            .indexOfFirst { it.component.packageName == component.packageName }
            .takeIf { it >= 0 }
            ?.let { return it }
        items
            .indexOfFirst { it.kind == DockItemKind.DYNAMIC || it.kind == DockItemKind.RECENT }
            .takeIf { it >= 0 }
            ?.let { return it }
        return if (items.size < maxItems) items.size else null
    }

    fun canLaunch(
        item: DockItem,
        requiresDistractionOptimization: Boolean,
    ): Boolean = !requiresDistractionOptimization || item.isDistractionOptimized || item.isMediaApp
}

interface DockRepository {
    val items: Flow<List<DockItem>>

    suspend fun pin(
        component: LauncherComponent,
        position: Int,
    ): Result<Unit>

    suspend fun unpin(component: LauncherComponent): Result<Unit>
}
