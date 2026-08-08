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
    val type: LaunchableAppType = LaunchableAppType.ACTIVITY,
    val isDistractionOptimized: Boolean,
    val isEnabled: Boolean,
    val disabledReason: LaunchableAppDisabledReason? = null,
)

enum class LaunchableAppType {
    ACTIVITY,
    MEDIA_SERVICE,
}

enum class LaunchableAppDisabledReason {
    NOT_DISTRACTION_OPTIMIZED,
    SAFETY_SERVICE_UNAVAILABLE,
}

data class LauncherRestrictions(
    val requiresDistractionOptimization: Boolean = true,
    val noKeyboard: Boolean = true,
    val carServiceReady: Boolean = false,
)

sealed interface EmbeddedTaskState {
    data object Idle : EmbeddedTaskState

    data class Loading(
        val target: EmbeddedAppTarget,
    ) : EmbeddedTaskState

    data class Running(
        val target: EmbeddedAppTarget,
    ) : EmbeddedTaskState

    data class Error(
        val error: EmbeddedTaskError,
    ) : EmbeddedTaskState
}

data class EmbeddedTaskError(
    val title: String,
    val message: String,
    val componentName: String? = null,
)

data class MediaPlayback(
    val sourcePackage: String? = null,
    val sourceComponent: String? = null,
    val sourceLabel: String = "",
    val title: String = "",
    val artist: String = "",
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

data class RecentTask(
    val taskId: Int,
    val componentName: String,
    val packageName: String,
    val label: String,
    val thumbnailBytes: ByteArray? = null,
    val isEnabled: Boolean = true,
)
