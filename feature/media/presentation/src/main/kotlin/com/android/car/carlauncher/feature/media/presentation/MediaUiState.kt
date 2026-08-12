package com.android.car.carlauncher.feature.media.presentation

import com.android.car.carlauncher.feature.media.domain.MediaCard

data class MediaUiState(
    val card: MediaCard? = null,
    val isRestricted: Boolean = false,
)
