package com.android.car.carlauncher.core.model

/** A stable launcher identity which can safely be persisted across process restarts. */
data class LauncherComponent(
    val packageName: String,
    val className: String,
    val userId: Int,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(className.isNotBlank()) { "className must not be blank" }
        require(userId >= 0) { "userId must be non-negative" }
    }

    val flattened: String
        get() = "$packageName/$className"
}

/** The target display selected by AAOS before launching or embedding an activity. */
data class DisplayTarget(
    val displayId: Int,
    val isDefaultDisplay: Boolean = false,
) {
    init {
        require(displayId >= 0) { "displayId must be non-negative" }
    }
}

/** Current UX policy as interpreted by a feature, independent of the car API implementation. */
enum class DrivingRestriction {
    UNRESTRICTED,
    NO_SETUP,
    NO_KEYBOARD,
    FULLY_RESTRICTED,
}

/** Minimal task data shared by HOME, TaskView, and recents without exposing hidden framework types. */
data class LauncherTask(
    val taskId: Int,
    val component: LauncherComponent?,
    val display: DisplayTarget,
    val isRunning: Boolean,
    val isVisible: Boolean,
) {
    init {
        require(taskId >= 0) { "taskId must be non-negative" }
    }
}
