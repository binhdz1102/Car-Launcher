package com.android.car.carlauncher.feature.appgrid.presentation

import com.android.car.carlauncher.feature.appgrid.domain.AppGridItem
import com.android.car.carlauncher.feature.appgrid.domain.AppGridMode
import com.android.car.carlauncher.feature.appgrid.domain.AppGridState

data class AppGridUiState(
    val state: AppGridState? = null,
    val visibleItems: List<AppGridItem> = emptyList(),
    val mode: AppGridMode = AppGridMode.ALL_APPS,
    val query: String = "",
    val canSearch: Boolean = false,
    val isReorderMode: Boolean = false,
) {
    val canReorder: Boolean get() = state?.canReorder == true && query.isBlank() && mode == AppGridMode.ALL_APPS
}
