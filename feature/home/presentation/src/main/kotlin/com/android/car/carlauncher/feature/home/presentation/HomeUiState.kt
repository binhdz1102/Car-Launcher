package com.android.car.carlauncher.feature.home.presentation

import com.android.car.carlauncher.feature.home.domain.HomePaneState

data class HomeUiState(
    val panes: List<HomePaneState> = emptyList(),
    val isLoading: Boolean = true,
)
