package com.android.car.carlauncher.feature.calmmode.presentation

import com.android.car.carlauncher.feature.calmmode.domain.CalmModeState
import com.android.car.carlauncher.feature.calmmode.domain.CalmTemperature
import com.android.car.carlauncher.feature.media.domain.MediaPlayback

data class CalmModeUiState(
    val state: CalmModeState = CalmModeState(enabled = false, title = "Calm mode"),
    val playback: MediaPlayback = MediaPlayback(),
    val temperature: CalmTemperature? = null,
    val updateInFlight: Boolean = false,
)
