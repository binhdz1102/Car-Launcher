package com.android.car.carlauncher.feature.launcher.domain

import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTargetType
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskError
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskState
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskTarget
import com.android.car.carlauncher.feature.home.domain.HomeTaskPaneMode

// Transitional source aliases. HOME owns TaskView state from this migration onward; legacy
// launcher consumers retain their source contract while their values are the HOME types.
typealias LauncherPaneMode = HomeTaskPaneMode
typealias EmbeddedTargetType = HomeEmbeddedTargetType
typealias EmbeddedAppTarget = HomeEmbeddedTaskTarget
typealias EmbeddedTaskState = HomeEmbeddedTaskState
typealias EmbeddedTaskError = HomeEmbeddedTaskError

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
