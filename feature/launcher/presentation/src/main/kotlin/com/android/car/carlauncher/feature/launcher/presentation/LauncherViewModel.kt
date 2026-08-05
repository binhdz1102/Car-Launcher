package com.android.car.carlauncher.feature.launcher.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedAppTarget
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTaskError
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTaskState
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTargetType
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.LauncherPaneMode
import com.android.car.carlauncher.feature.launcher.domain.LauncherPaneStateMachine
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaQueueItem
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import timber.log.Timber

data class LauncherUiState(
    val paneMode: LauncherPaneMode = LauncherPaneMode.NAVIGATION,
    val taskState: EmbeddedTaskState = EmbeddedTaskState.Idle,
    val apps: List<LaunchableApp> = emptyList(),
    val media: MediaPlayback = MediaPlayback(),
    val sources: List<MediaSource> = emptyList(),
    val queue: List<MediaQueueItem> = emptyList(),
) {
    val currentTarget: EmbeddedAppTarget?
        get() = when (taskState) {
            is EmbeddedTaskState.Loading -> taskState.target
            is EmbeddedTaskState.Running -> taskState.target
            else -> null
        }

    val error: EmbeddedTaskError?
        get() = (taskState as? EmbeddedTaskState.Error)?.error
}

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val launcherAppsRepository: LauncherAppsRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val machine = LauncherPaneStateMachine()
    private val mutableUiState = MutableStateFlow(LauncherUiState())
    private var selectionJob: Job? = null

    val uiState: StateFlow<LauncherUiState> = combine(
        mutableUiState,
        mediaRepository.playback,
        mediaRepository.sources,
        mediaRepository.queue,
    ) { state, media, sources, queue ->
        state.copy(media = media, sources = sources, queue = queue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        viewModelScope.launch {
            launcherAppsRepository.launchableApps.collect { apps ->
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
        selectNavigation()
    }

    fun handleHostIntent(intent: Intent?) {
        if (intent == null) return
        when {
            intent.getBooleanExtra(EXTRA_SELECT_NAVIGATION, false) -> selectNavigation()
            intent.hasExtra(EXTRA_EMBEDDED_COMPONENT) -> {
                val component = intent.getStringExtra(EXTRA_EMBEDDED_COMPONENT).orEmpty()
                if (component.isNotBlank()) {
                    selectTarget(
                        EmbeddedAppTarget(
                            componentName = component,
                            label = intent.getStringExtra(EXTRA_EMBEDDED_LABEL).orEmpty()
                                .ifBlank { component.substringAfterLast('/') },
                            type = EmbeddedTargetType.APPLICATION,
                        ),
                    )
                }
            }
        }
    }

    fun selectNavigation() {
        viewModelScope.launch {
            launcherAppsRepository.navigationTarget().fold(
                onSuccess = ::selectTarget,
                onFailure = {
                    onEmbeddedFailure(
                        "Navigation unavailable",
                        it.message ?: "No map application is installed for this vehicle user.",
                    )
                },
            )
        }
    }

    fun onTaskAppeared(componentName: String) {
        val next = runCatching { machine.appeared(componentName) }
            .getOrElse { machine.failed("Embedded task mismatch", it.message.orEmpty()) }
        mutableUiState.value = mutableUiState.value.copy(
            paneMode = when (next) {
                is EmbeddedTaskState.Running -> if (
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

    fun onEmbeddedFailure(title: String, message: String) {
        Timber.tag(TAG).w("Embedded task failure: %s - %s", title, message)
        mutableUiState.value = mutableUiState.value.copy(
            paneMode = LauncherPaneMode.ERROR,
            taskState = machine.failed(title, message),
        )
    }

    fun selectApp(app: LaunchableApp) {
        if (!app.isEnabled || selectionJob?.isActive == true) return
        selectionJob = viewModelScope.launch {
            selectTarget(
                EmbeddedAppTarget(
                    componentName = app.componentName,
                    label = app.label,
                    type = EmbeddedTargetType.APPLICATION,
                ),
            )
        }
    }

    fun playPause() = mediaRepository.playPause()
    fun previous() = mediaRepository.previous()
    fun next() = mediaRepository.next()
    fun seekTo(positionMs: Long) = mediaRepository.seekTo(positionMs)
    fun selectSource(source: MediaSource) = mediaRepository.selectSource(source)
    fun openMediaCenter() = mediaRepository.openMediaCenter()

    private fun selectTarget(target: EmbeddedAppTarget) {
        mutableUiState.value = mutableUiState.value.copy(
            paneMode = if (target.type == EmbeddedTargetType.NAVIGATION) {
                LauncherPaneMode.NAVIGATION
            } else {
                LauncherPaneMode.EMBEDDED_APP
            },
            taskState = machine.select(target),
        )
        Timber.tag(TAG).d("Requested embedded target: %s", target.componentName)
    }

    private companion object {
        const val TAG = "CarLauncher.LauncherViewModel"
    }
}
