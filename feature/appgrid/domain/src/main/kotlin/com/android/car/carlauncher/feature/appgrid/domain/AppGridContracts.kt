package com.android.car.carlauncher.feature.appgrid.domain

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.model.LauncherComponent
import kotlinx.coroutines.flow.StateFlow

enum class AppGridOrientation {
    HORIZONTAL,
    VERTICAL,
}

/** Modes accepted by the exported AAOS app-grid activity. */
enum class AppGridMode(
    val includesLauncherActivities: Boolean,
    val opensMediaCenter: Boolean,
) {
    ALL_APPS(includesLauncherActivities = true, opensMediaCenter = true),
    MEDIA_ONLY(includesLauncherActivities = false, opensMediaCenter = true),
    MEDIA_POPUP(includesLauncherActivities = false, opensMediaCenter = false),
    ;

    companion object {
        const val INTENT_EXTRA = "com.android.car.carlauncher.mode"

        /**
         * A missing extra means the public ACTION_APP_GRID contract's default ALL_APPS mode.
         * Unknown non-null values are rejected like the AOSP exported activity so malformed
         * external intents cannot silently open a different surface.
         */
        fun fromIntentValue(value: String?): AppGridMode {
            if (value == null) return ALL_APPS
            return entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Received invalid app-grid mode: $value")
        }
    }
}

enum class AppGridItemType {
    ACTIVITY,
    MEDIA_SERVICE,
    DISABLED_ACTIVITY,
    TOS_RESTRICTED_ACTIVITY,
}

enum class AppGridAvailability {
    AVAILABLE,
    REQUIRES_DISTRACTION_OPTIMIZATION,
    CAR_SERVICE_UNAVAILABLE,
    TOS_REVIEW_REQUIRED,
}

data class AppGridItem(
    val component: LauncherComponent,
    val label: String,
    val type: AppGridItemType,
    val isRecent: Boolean,
    val isDistractionOptimized: Boolean,
    val availability: AppGridAvailability,
    /** An AAOS Control Center redirect represented without leaking Intent into the domain. */
    val mirroringRedirectUri: String? = null,
)

data class AppGridShortcut(
    val id: String,
    val shortLabel: String,
    val longLabel: String? = null,
)

data class AppGridState(
    val items: List<AppGridItem>,
    val orientation: AppGridOrientation,
    val restriction: DrivingRestriction,
    val tosAccepted: Boolean,
    val shouldShowTosBanner: Boolean,
    val canReorder: Boolean,
)

interface AppGridRepository {
    val state: StateFlow<AppGridState>

    suspend fun reorder(
        fromIndex: Int,
        toIndex: Int,
    ): Result<Unit>

    suspend fun saveOrder(order: List<LauncherComponent>): Result<Unit>

    suspend fun clearOrder(): Result<Unit>

    suspend fun launch(
        item: AppGridItem,
        display: DisplayTarget,
        mode: AppGridMode,
    ): Result<Unit>

    suspend fun shortcuts(item: AppGridItem): Result<List<AppGridShortcut>>

    suspend fun launchShortcut(
        item: AppGridItem,
        shortcut: AppGridShortcut,
        display: DisplayTarget,
    ): Result<Unit>

    suspend fun dismissTosBanner()

    suspend fun reviewTos(display: DisplayTarget): Result<Unit>
}
