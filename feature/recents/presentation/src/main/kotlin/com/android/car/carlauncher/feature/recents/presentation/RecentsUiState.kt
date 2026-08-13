package com.android.car.carlauncher.feature.recents.presentation

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.feature.recents.domain.RecentTask

data class RecentsUiState(
    val tasks: List<RecentTask> = emptyList(),
    val display: DisplayTarget = DisplayTarget(displayId = 0, isDefaultDisplay = true),
    val isLoading: Boolean = true,
)
