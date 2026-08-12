package com.android.car.carlauncher.feature.appgrid.presentation

import com.android.car.carlauncher.feature.appgrid.domain.AppGridState

data class AppGridUiState(
    val state: AppGridState? = null,
    val query: String = "",
)
