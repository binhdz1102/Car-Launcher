package com.android.car.carlauncher.feature.launcher.domain

/** The surface shown in the right-hand pane of the Automotive home screen. */
enum class LauncherPaneMode {
    NAVIGATION,
    EMBEDDED_APP,
    ERROR,
}

enum class EmbeddedTargetType { NAVIGATION, APPLICATION }

data class EmbeddedAppTarget(
    val componentName: String,
    val label: String,
    val type: EmbeddedTargetType,
)

data class LaunchableApp(
    val componentName: String,
    val packageName: String,
    val label: String,
    val isDistractionOptimized: Boolean,
    val isEnabled: Boolean,
    val disabledReason: String? = null,
)

sealed interface EmbeddedTaskState {
    data object Idle : EmbeddedTaskState
    data class Loading(val target: EmbeddedAppTarget) : EmbeddedTaskState
    data class Running(val target: EmbeddedAppTarget) : EmbeddedTaskState
    data class Error(val error: EmbeddedTaskError) : EmbeddedTaskState
}

data class EmbeddedTaskError(
    val title: String,
    val message: String,
    val componentName: String? = null,
)

data class MediaPlayback(
    val sourcePackage: String? = null,
    val sourceLabel: String = "No media source",
    val title: String = "Nothing playing",
    val artist: String = "Choose a media source to begin",
    val artworkBytes: ByteArray? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSeek: Boolean = false,
)

data class MediaSource(
    val componentName: String,
    val label: String,
)

data class MediaQueueItem(
    val id: Long,
    val title: String,
    val subtitle: String,
)
