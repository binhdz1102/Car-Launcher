package com.android.car.carlauncher.feature.recents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.feature.recents.domain.RecentTask
import com.android.car.carlauncher.feature.recents.domain.RecentsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Presentation state and user actions for the platform Recents surface. */
@HiltViewModel
class RecentsViewModel
    @Inject
    constructor(
        private val repository: RecentsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<RecentsUiState> =
            repository.state
                .map { state ->
                    RecentsUiState(
                        tasks = state.tasks,
                        display = state.display,
                        isLoading = state.isLoading,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = RecentsUiState(),
                )

        fun refresh(display: DisplayTarget) {
            viewModelScope.launch { repository.refresh(display) }
        }

        fun launch(task: RecentTask) {
            viewModelScope.launch {
                repository.launch(task).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to open task=%d", task.taskId)
                }
            }
        }

        fun launchTopRunningTask(
            display: DisplayTarget,
            onFailure: () -> Unit,
        ) {
            viewModelScope.launch {
                repository.launchTopRunningTask(display).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to open task behind Recents")
                    onFailure()
                }
            }
        }

        fun dismiss(task: RecentTask) {
            viewModelScope.launch {
                repository.dismiss(task).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to remove task=%d", task.taskId)
                }
            }
        }

        fun clearAll() {
            viewModelScope.launch {
                repository.clearAll().onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to clear recent tasks")
                }
            }
        }

        private companion object {
            const val TAG = "CarLauncher.RecentsViewModel"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
