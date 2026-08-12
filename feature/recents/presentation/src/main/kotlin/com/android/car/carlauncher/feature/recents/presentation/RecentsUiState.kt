package com.android.car.carlauncher.feature.recents.presentation

import com.android.car.carlauncher.core.model.LauncherTask

data class RecentsUiState(
    val tasks: List<LauncherTask> = emptyList(),
    val isLoading: Boolean = true,
)
