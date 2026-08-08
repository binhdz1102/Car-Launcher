package com.android.car.carlauncher.feature.launcher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.launcher.domain.RecentTask
import com.android.car.carlauncher.feature.launcher.domain.RecentTasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecentsViewModel
    @Inject
    constructor(
        private val repository: RecentTasksRepository,
    ) : ViewModel() {
        val tasks: StateFlow<List<RecentTask>> = repository.tasks

        fun refresh(displayId: Int) {
            viewModelScope.launch { repository.refresh(displayId) }
        }

        fun open(task: RecentTask) {
            viewModelScope.launch {
                repository.open(task).onFailure {
                    Timber.tag(TAG).w(it, "Unable to open task=%d", task.taskId)
                }
            }
        }

        fun openTopRunningTask(
            displayId: Int,
            onFailure: () -> Unit,
        ) {
            viewModelScope.launch {
                repository.openTopRunningTask(displayId).onFailure {
                    Timber.tag(TAG).w(it, "Unable to dismiss Recents to the previous task")
                    onFailure()
                }
            }
        }

        fun remove(task: RecentTask) {
            viewModelScope.launch {
                repository.remove(task).onFailure {
                    Timber.tag(TAG).w(it, "Unable to remove task=%d", task.taskId)
                }
            }
        }

        fun clearAll() {
            viewModelScope.launch {
                repository.clearAll().onFailure {
                    Timber.tag(TAG).w(it, "Unable to clear recent tasks")
                }
            }
        }

        private companion object {
            const val TAG = "CarLauncher.RecentsViewModel"
        }
    }
