package com.android.car.carlauncher.feature.launcher.presentation

import androidx.lifecycle.ViewModel
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CalmModeViewModel
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
    ) : ViewModel() {
        val playback = mediaRepository.playback

        fun playPause() = mediaRepository.playPause()

        fun previous() = mediaRepository.previous()

        fun next() = mediaRepository.next()

        fun openMediaCenter() = mediaRepository.openMediaCenter()
    }
