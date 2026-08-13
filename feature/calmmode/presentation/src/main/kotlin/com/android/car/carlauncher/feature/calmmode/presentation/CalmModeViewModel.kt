package com.android.car.carlauncher.feature.calmmode.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.calmmode.domain.CalmModeRepository
import com.android.car.carlauncher.feature.calmmode.domain.TemperatureRepository
import com.android.car.carlauncher.feature.media.domain.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Calm Mode presenter: passive clock/date surface plus the active media title. */
@HiltViewModel
class CalmModeViewModel
    @Inject
    constructor(
        private val calmModeRepository: CalmModeRepository,
        private val mediaRepository: MediaRepository,
        private val temperatureRepository: TemperatureRepository,
    ) : ViewModel() {
        val uiState: StateFlow<CalmModeUiState> =
            combine(
                calmModeRepository.state,
                mediaRepository.playback,
                temperatureRepository.temperature,
            ) { calmMode, playback, temperature ->
                CalmModeUiState(state = calmMode, playback = playback, temperature = temperature)
            }.onStart { emit(CalmModeUiState()) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = CalmModeUiState(),
                )

        fun onStarted() {
            setEnabled(true)
        }

        fun onStopped() {
            setEnabled(false)
        }

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch {
                calmModeRepository.setEnabled(enabled).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to persist Calm Mode state=%s", enabled)
                }
            }
        }

        private companion object {
            const val TAG = "CarLauncher.CalmModeViewModel"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
