package com.android.car.carlauncher.feature.appgrid.domain

import com.android.car.carlauncher.core.model.DrivingRestriction

/** Pure app-grid policy, kept independent of Android services and persistence. */
object AppGridStateReducer {
    fun recentItems(
        items: List<AppGridItem>,
        mode: AppGridMode,
    ): List<AppGridItem> = filter(items.filter(AppGridItem::isRecent), mode, query = "")

    fun arrange(
        discovered: List<AppGridItem>,
        orderedComponents: List<String>,
    ): List<AppGridItem> {
        val orderIndex = orderedComponents.withIndex().associate { (index, component) -> component to index }
        return discovered
            .distinctBy { it.component.flattened }
            .sortedWith(
                compareBy<AppGridItem> { orderIndex[it.component.flattened] ?: Int.MAX_VALUE }
                    .thenBy { it.label.lowercase() },
            )
    }

    fun filter(
        items: List<AppGridItem>,
        mode: AppGridMode,
        query: String,
    ): List<AppGridItem> =
        items.filter { item ->
            (mode.includesLauncherActivities || item.type == AppGridItemType.MEDIA_SERVICE) &&
                (query.isBlank() || item.label.contains(query, ignoreCase = true))
        }

    fun canReorder(
        restriction: DrivingRestriction,
        query: String,
        mode: AppGridMode,
    ): Boolean = restriction == DrivingRestriction.UNRESTRICTED && query.isBlank() && mode == AppGridMode.ALL_APPS
}
