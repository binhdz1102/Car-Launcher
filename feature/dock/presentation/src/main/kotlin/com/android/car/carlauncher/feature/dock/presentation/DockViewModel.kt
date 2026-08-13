package com.android.car.carlauncher.feature.dock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.feature.dock.domain.DockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DockViewModel
    @Inject
    constructor(
        private val repository: DockRepository,
    ) : ViewModel() {
        val uiState: StateFlow<DockUiState> =
            repository.items
                .map { items -> DockUiState(items = items) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = DockUiState(),
                )

        fun pin(
            component: LauncherComponent,
            position: Int,
        ) {
            viewModelScope.launch {
                repository.pin(component, position).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to pin Dock item=%s", component.flattened)
                }
            }
        }

        fun unpin(component: LauncherComponent) {
            viewModelScope.launch {
                repository.unpin(component).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to unpin Dock item=%s", component.flattened)
                }
            }
        }

        private companion object {
            const val TAG = "CarLauncher.DockViewModel"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
