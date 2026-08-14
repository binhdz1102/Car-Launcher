package com.android.car.carlauncher.feature.launcher.domain

import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTargetType
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskError
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskState
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskTarget
import com.android.car.carlauncher.feature.home.domain.HomeTaskPaneMode
import com.android.car.carlauncher.feature.media.domain.MediaCustomAction as FeatureMediaCustomAction
import com.android.car.carlauncher.feature.media.domain.MediaHistoryItem as FeatureMediaHistoryItem
import com.android.car.carlauncher.feature.media.domain.MediaPlayback as FeatureMediaPlayback
import com.android.car.carlauncher.feature.media.domain.MediaQueueItem as FeatureMediaQueueItem
import com.android.car.carlauncher.feature.media.domain.MediaRepository as FeatureMediaRepository
import com.android.car.carlauncher.feature.media.domain.MediaSource as FeatureMediaSource

// Transitional source aliases. HOME owns TaskView state from this migration onward; legacy
// launcher consumers retain their source contract while their values are the HOME types.
typealias LauncherPaneMode = HomeTaskPaneMode
typealias EmbeddedTargetType = HomeEmbeddedTargetType
typealias EmbeddedAppTarget = HomeEmbeddedTaskTarget
typealias EmbeddedTaskState = HomeEmbeddedTaskState
typealias EmbeddedTaskError = HomeEmbeddedTaskError

// MEDIA now owns its models and platform adapter. These aliases preserve the legacy launcher API
// until the HOME presentation contract is moved in its own migration slice.
typealias MediaPlayback = FeatureMediaPlayback
typealias MediaSource = FeatureMediaSource
typealias MediaQueueItem = FeatureMediaQueueItem
typealias MediaHistoryItem = FeatureMediaHistoryItem
typealias MediaCustomAction = FeatureMediaCustomAction
typealias MediaRepository = FeatureMediaRepository

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
