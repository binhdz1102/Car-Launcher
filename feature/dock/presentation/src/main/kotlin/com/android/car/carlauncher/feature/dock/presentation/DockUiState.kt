package com.android.car.carlauncher.feature.dock.presentation

import com.android.car.carlauncher.feature.dock.domain.DockItem

data class DockUiState(
    val items: List<DockItem> = emptyList(),
    val isEditing: Boolean = false,
)
