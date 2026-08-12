package com.android.car.carlauncher.core.platform

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.core.model.LauncherTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle state of a connection to an AAOS service or SystemUI binder. */
sealed interface PlatformConnectionState {
    data object Disconnected : PlatformConnectionState

    data object Connecting : PlatformConnectionState

    data object Connected : PlatformConnectionState

    data class Failed(
        val cause: Throwable?,
    ) : PlatformConnectionState
}

/** Events originating from task lifecycle callbacks, normalized for feature modules. */
sealed interface PlatformTaskEvent {
    data class Appeared(
        val task: LauncherTask,
    ) : PlatformTaskEvent

    data class Changed(
        val task: LauncherTask,
    ) : PlatformTaskEvent

    data class Vanished(
        val taskId: Int,
        val display: DisplayTarget,
    ) : PlatformTaskEvent
}

/** A Flow-first boundary for the hidden ActivityTaskManager and TaskView APIs. */
interface TaskController {
    val connection: StateFlow<PlatformConnectionState>
    val taskEvents: Flow<PlatformTaskEvent>

    suspend fun launch(
        component: LauncherComponent,
        display: DisplayTarget,
    ): Result<Unit>

    suspend fun remove(taskId: Int): Result<Unit>
}

/** A Flow-first boundary for the car UX-restriction callback. */
interface DrivingRestrictionMonitor {
    val restrictions: StateFlow<DrivingRestriction>
}

/** A Flow-first boundary for package add/change/remove callbacks. */
interface PackageChangeMonitor {
    val packageChanges: Flow<PackageChange>
}

data class PackageChange(
    val packageName: String,
    val userId: Int,
    val type: Type,
) {
    enum class Type {
        ADDED,
        CHANGED,
        REMOVED,
    }
}
