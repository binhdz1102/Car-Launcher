package com.android.car.carlauncher.feature.calmmode.presentation

import com.android.car.carlauncher.feature.calmmode.domain.CalmModeState

data class CalmModeUiState(
    val state: CalmModeState? = null,
    val updateInFlight: Boolean = false,
)
