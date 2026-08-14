package com.android.car.carlauncher.feature.launcher.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTargetType
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskError
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskState
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskTarget
import com.android.car.carlauncher.feature.home.domain.HomeTaskPaneMode
import com.android.car.carlauncher.feature.home.domain.HomeTaskStateMachine
import com.android.car.carlauncher.feature.home.domain.NavigationTargetInvalidation
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaCustomAction
import com.android.car.carlauncher.feature.launcher.domain.MediaHistoryItem
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaQueueItem
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import com.android.car.carlauncher.feature.media.domain.AssistiveCard
import com.android.car.carlauncher.feature.media.domain.AssistiveRepository
import com.android.car.carlauncher.feature.media.domain.CallCard
import com.android.car.carlauncher.feature.media.domain.CallRepository
import com.android.car.carlauncher.feature.media.domain.HomeCardCoordinator
import com.android.car.carlauncher.feature.media.domain.ProjectionRepository
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
    val paneMode: HomeTaskPaneMode = HomeTaskPaneMode.NAVIGATION,
    val taskState: HomeEmbeddedTaskState = HomeEmbeddedTaskState.Idle,
    val apps: List<LaunchableApp> = emptyList(),
    val media: MediaPlayback = MediaPlayback(),
    val sources: List<MediaSource> = emptyList(),
    val queue: List<MediaQueueItem> = emptyList(),
    val history: List<MediaHistoryItem> = emptyList(),
    val mediaVariant: com.android.car.carlauncher.feature.media.domain.MediaCardVariant =
        com.android.car.carlauncher.feature.media.domain.MediaCardVariant.EMPTY,
    val activeCall: CallCard? = null,
    val assistive: AssistiveCard? = null,
) {
    val currentTarget: HomeEmbeddedTaskTarget?
        get() =
            when (taskState) {
                is HomeEmbeddedTaskState.Loading -> taskState.target
                is HomeEmbeddedTaskState.Running -> taskState.target
                else -> null
            }

    val error: HomeEmbeddedTaskError?
        get() = (taskState as? HomeEmbeddedTaskState.Error)?.error
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

    data class CustomAction(
        val action: MediaCustomAction,
    ) : LauncherMediaAction
}

sealed interface LauncherHomeCardAction {
    data object ToggleCallMute : LauncherHomeCardAction

    data object EndCall : LauncherHomeCardAction

    data object OpenDialpad : LauncherHomeCardAction

    data class LaunchAssistive(
        val card: AssistiveCard,
    ) : LauncherHomeCardAction
}

@HiltViewModel
@Suppress("TooManyFunctions") // HOME selection and card actions share one lifecycle-aware state owner.
class LauncherViewModel
    @Inject
    constructor(
        private val launcherAppsRepository: LauncherAppsRepository,
        private val mediaRepository: MediaRepository,
        private val homeCardCoordinator: HomeCardCoordinator,
        private val callRepository: CallRepository,
        private val projectionRepository: ProjectionRepository,
        private val assistiveRepository: AssistiveRepository,
        private val navigationTargetInvalidation: NavigationTargetInvalidation,
    ) : ViewModel() {
        private val machine = HomeTaskStateMachine()
        private val mutableUiState = MutableStateFlow(LauncherUiState())
        private var selectionJob: Job? = null
        private var selectionGeneration = 0L

        private val mediaSnapshot =
            combine(
                mediaRepository.playback,
                mediaRepository.sources,
                mediaRepository.queue,
                mediaRepository.history,
            ) { media, sources, queue, history ->
                MediaSnapshot(media, sources, queue, history)
            }

        val uiState: StateFlow<LauncherUiState> =
            combine(
                mutableUiState,
                mediaSnapshot,
                homeCardCoordinator.states,
            ) { state, media, homeCards ->
                state.copy(
                    media = media.playback,
                    sources = media.sources,
                    queue = media.queue,
                    history = media.history,
                    mediaVariant = homeCards.mediaVariant,
                    activeCall = homeCards.activeCall,
                    assistive = homeCards.assistive,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

        init {
            viewModelScope.launch {
                launcherAppsRepository.launchableApps.collectLatest { apps ->
                    mutableUiState.value = mutableUiState.value.copy(apps = apps)
                    val current = machine.currentTarget()
                    if (current?.type == HomeEmbeddedTargetType.APPLICATION &&
                        apps.none { it.componentName == current.componentName }
                    ) {
                        onEmbeddedFailure(
                            "Application removed",
                            "The selected application is no longer available for this vehicle user.",
                        )
                    }
                }
            }
            viewModelScope.launch {
                navigationTargetInvalidation.changes.collectLatest {
                    requestNavigationSelection()
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
                            is HomeEmbeddedTaskState.Running ->
                                if (
                                    next.target.type == HomeEmbeddedTargetType.NAVIGATION
                                ) {
                                    HomeTaskPaneMode.NAVIGATION
                                } else {
                                    HomeTaskPaneMode.EMBEDDED_APP
                                }
                            is HomeEmbeddedTaskState.Error -> HomeTaskPaneMode.ERROR
                            else -> machine.currentMode()
                        },
                    taskState = next,
                )
            Timber.tag(TAG).i("Embedded task appeared: %s", componentName)
        }

        fun onTaskInfoChanged(componentName: String) {
            if (machine.currentTarget()?.componentName == componentName) {
                onTaskAppeared(componentName)
            } else {
                Timber.tag(TAG).d("Ignored TaskView update for %s", componentName)
            }
        }

        fun onTaskViewRecovering() {
            val target = machine.currentTarget() ?: return
            mutableUiState.value =
                mutableUiState.value.copy(
                    paneMode =
                        if (target.type == HomeEmbeddedTargetType.NAVIGATION) {
                            HomeTaskPaneMode.NAVIGATION
                        } else {
                            HomeTaskPaneMode.EMBEDDED_APP
                        },
                    taskState = machine.select(target),
                )
        }

        fun onEmbeddedFailure(
            title: String,
            message: String,
        ) {
            Timber.tag(TAG).w("Embedded task failure: %s - %s", title, message)
            mutableUiState.value =
                mutableUiState.value.copy(
                    paneMode = HomeTaskPaneMode.ERROR,
                    taskState = machine.failed(title, message),
                )
        }

        fun selectApp(app: LaunchableApp) {
            if (!app.isEnabled) return
            selectionJob?.cancel()
            selectTarget(
                HomeEmbeddedTaskTarget(
                    componentName = app.componentName,
                    label = app.label,
                    type = HomeEmbeddedTargetType.APPLICATION,
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
                is LauncherMediaAction.CustomAction -> mediaRepository.sendCustomAction(action.action)
            }
        }

        fun handleHomeCardAction(action: LauncherHomeCardAction) {
            when (action) {
                LauncherHomeCardAction.ToggleCallMute -> callRepository.toggleMute()
                LauncherHomeCardAction.EndCall -> callRepository.endCall()
                LauncherHomeCardAction.OpenDialpad -> callRepository.openDialpad()
                is LauncherHomeCardAction.LaunchAssistive -> {
                    if (action.card.id.startsWith("projection:")) {
                        projectionRepository.launchCurrentProjection()
                    } else {
                        assistiveRepository.launch(action.card)
                    }
                }
            }
        }

        private fun selectTarget(target: HomeEmbeddedTaskTarget) {
            selectionGeneration++
            selectTargetWithoutInvalidatingGeneration(target)
        }

        private fun selectTargetWithoutInvalidatingGeneration(target: HomeEmbeddedTaskTarget) {
            mutableUiState.value =
                mutableUiState.value.copy(
                    paneMode =
                        if (target.type == HomeEmbeddedTargetType.NAVIGATION) {
                            HomeTaskPaneMode.NAVIGATION
                        } else {
                            HomeTaskPaneMode.EMBEDDED_APP
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

        private data class MediaSnapshot(
            val playback: MediaPlayback,
            val sources: List<MediaSource>,
            val queue: List<MediaQueueItem>,
            val history: List<MediaHistoryItem>,
        )
    }
