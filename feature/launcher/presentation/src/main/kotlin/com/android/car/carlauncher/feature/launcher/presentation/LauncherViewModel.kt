package com.android.car.carlauncher.feature.launcher.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedAppTarget
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTargetType
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTaskError
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTaskState
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.LauncherPaneMode
import com.android.car.carlauncher.feature.launcher.domain.LauncherPaneStateMachine
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaQueueItem
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LauncherUiState(
    val paneMode: LauncherPaneMode = LauncherPaneMode.NAVIGATION,
    val taskState: EmbeddedTaskState = EmbeddedTaskState.Idle,
    val apps: List<LaunchableApp> = emptyList(),
    val media: MediaPlayback = MediaPlayback(),
    val sources: List<MediaSource> = emptyList(),
    val queue: List<MediaQueueItem> = emptyList(),
) {
    val currentTarget: EmbeddedAppTarget?
        get() =
            when (taskState) {
                is EmbeddedTaskState.Loading -> taskState.target
                is EmbeddedTaskState.Running -> taskState.target
                else -> null
            }

    val error: EmbeddedTaskError?
        get() = (taskState as? EmbeddedTaskState.Error)?.error
}

sealed interface LauncherMediaAction {
    data object PlayPause : LauncherMediaAction

    data object Previous : LauncherMediaAction

    data object Next : LauncherMediaAction

    data object OpenCenter : LauncherMediaAction

    data class Seek(
        val positionMs: Long,
    ) : LauncherMediaAction

    data class SelectSource(
        val source: MediaSource,
    ) : LauncherMediaAction
}

@HiltViewModel
class LauncherViewModel
    @Inject
    constructor(
        private val launcherAppsRepository: LauncherAppsRepository,
        private val mediaRepository: MediaRepository,
    ) : ViewModel() {
        private val machine = LauncherPaneStateMachine()
        private val mutableUiState = MutableStateFlow(LauncherUiState())
        private var selectionJob: Job? = null
        private var selectionGeneration = 0L

        val uiState: StateFlow<LauncherUiState> =
            combine(
                mutableUiState,
                mediaRepository.playback,
                mediaRepository.sources,
                mediaRepository.queue,
            ) { state, media, sources, queue ->
                state.copy(media = media, sources = sources, queue = queue)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

        init {
            viewModelScope.launch {
                launcherAppsRepository.launchableApps.collectLatest { apps ->
                    mutableUiState.value = mutableUiState.value.copy(apps = apps)
                    val current = machine.currentTarget()
                    if (current?.type == EmbeddedTargetType.APPLICATION &&
                        apps.none { it.componentName == current.componentName }
                    ) {
                        onEmbeddedFailure(
                            "Application removed",
                            "The selected application is no longer available for this vehicle user.",
                        )
                    }
                }
            }
            requestNavigationSelection()
        }

        fun handleHostIntent(intent: Intent?) {
            if (intent == null) return
            if (intent.getBooleanExtra(EXTRA_SELECT_NAVIGATION, false)) {
                requestNavigationSelection()
            }
        }

        fun selectNavigation() {
            requestNavigationSelection()
        }

        fun onTaskAppeared(componentName: String) {
            val next =
                runCatching { machine.appeared(componentName) }
                    .getOrElse { machine.failed("Embedded task mismatch", it.message.orEmpty()) }
            mutableUiState.value =
                mutableUiState.value.copy(
                    paneMode =
                        when (next) {
                            is EmbeddedTaskState.Running ->
                                if (
                                    next.target.type == EmbeddedTargetType.NAVIGATION
                                ) {
                                    LauncherPaneMode.NAVIGATION
                                } else {
                                    LauncherPaneMode.EMBEDDED_APP
                                }
                            is EmbeddedTaskState.Error -> LauncherPaneMode.ERROR
                            else -> machine.currentMode()
                        },
                    taskState = next,
                )
            Timber.tag(TAG).i("Embedded task appeared: %s", componentName)
        }

        fun onEmbeddedFailure(
            title: String,
            message: String,
        ) {
            Timber.tag(TAG).w("Embedded task failure: %s - %s", title, message)
            mutableUiState.value =
                mutableUiState.value.copy(
                    paneMode = LauncherPaneMode.ERROR,
                    taskState = machine.failed(title, message),
                )
        }

        fun selectApp(app: LaunchableApp) {
            if (!app.isEnabled) return
            selectionJob?.cancel()
            selectTarget(
                EmbeddedAppTarget(
                    componentName = app.componentName,
                    label = app.label,
                    type = EmbeddedTargetType.APPLICATION,
                ),
            )
        }

        fun handleMediaAction(action: LauncherMediaAction) {
            when (action) {
                LauncherMediaAction.PlayPause -> mediaRepository.playPause()
                LauncherMediaAction.Previous -> mediaRepository.previous()
                LauncherMediaAction.Next -> mediaRepository.next()
                LauncherMediaAction.OpenCenter -> mediaRepository.openMediaCenter()
                is LauncherMediaAction.Seek -> mediaRepository.seekTo(action.positionMs)
                is LauncherMediaAction.SelectSource -> mediaRepository.selectSource(action.source)
            }
        }

        private fun selectTarget(target: EmbeddedAppTarget) {
            selectionGeneration++
            selectTargetWithoutInvalidatingGeneration(target)
        }

        private fun selectTargetWithoutInvalidatingGeneration(target: EmbeddedAppTarget) {
            mutableUiState.value =
                mutableUiState.value.copy(
                    paneMode =
                        if (target.type == EmbeddedTargetType.NAVIGATION) {
                            LauncherPaneMode.NAVIGATION
                        } else {
                            LauncherPaneMode.EMBEDDED_APP
                        },
                    taskState = machine.select(target),
                )
            Timber.tag(TAG).d("Requested embedded target: %s", target.componentName)
        }

        /** Ignores a slow Navigation resolver result after a newer user selection. */
        private fun requestNavigationSelection() {
            val generation = ++selectionGeneration
            selectionJob?.cancel()
            selectionJob =
                viewModelScope.launch {
                    launcherAppsRepository.navigationTarget().fold(
                        onSuccess = { target ->
                            if (generation == selectionGeneration) {
                                selectTargetWithoutInvalidatingGeneration(target)
                            } else {
                                Timber.tag(TAG).d(
                                    "Ignored stale Navigation result=%s",
                                    target.componentName,
                                )
                            }
                        },
                        onFailure = {
                            if (generation == selectionGeneration) {
                                onEmbeddedFailure(
                                    "Navigation unavailable",
                                    it.message ?: "No map application is installed for this vehicle user.",
                                )
                            }
                        },
                    )
                }
        }

        private companion object {
            const val TAG = "CarLauncher.LauncherViewModel"
        }
    }
