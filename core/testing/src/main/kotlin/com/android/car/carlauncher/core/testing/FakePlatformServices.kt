package com.android.car.carlauncher.core.testing

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.core.platform.DrivingRestrictionMonitor
import com.android.car.carlauncher.core.platform.PackageChange
import com.android.car.carlauncher.core.platform.PackageChangeMonitor
import com.android.car.carlauncher.core.platform.PlatformConnectionState
import com.android.car.carlauncher.core.platform.PlatformTaskEvent
import com.android.car.carlauncher.core.platform.TaskController
import com.android.car.carlauncher.core.platform.UxrState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deterministic fake shared by unit and instrumentation tests without Android service calls. */
class FakeTaskController : TaskController {
    private val mutableConnection = MutableStateFlow<PlatformConnectionState>(PlatformConnectionState.Disconnected)
    private val mutableTaskEvents = MutableSharedFlow<PlatformTaskEvent>(extraBufferCapacity = 16)

    override val connection: StateFlow<PlatformConnectionState> = mutableConnection.asStateFlow()
    override val taskEvents: Flow<PlatformTaskEvent> = mutableTaskEvents.asSharedFlow()

    val launches = mutableListOf<Pair<LauncherComponent, DisplayTarget>>()
    val removals = mutableListOf<Int>()
    var nextLaunchResult: Result<Unit> = Result.success(Unit)
    var nextRemoveResult: Result<Unit> = Result.success(Unit)

    override suspend fun launch(
        component: LauncherComponent,
        display: DisplayTarget,
    ): Result<Unit> {
        launches += component to display
        return nextLaunchResult
    }

    override suspend fun remove(taskId: Int): Result<Unit> {
        removals += taskId
        return nextRemoveResult
    }

    fun setConnection(state: PlatformConnectionState) {
        mutableConnection.value = state
    }

    fun emit(event: PlatformTaskEvent) {
        check(mutableTaskEvents.tryEmit(event)) { "Fake task event buffer is full" }
    }
}

class FakeDrivingRestrictionMonitor(
    initial: UxrState = UxrState.Unavailable,
) : DrivingRestrictionMonitor {
    private val mutableRestrictions = MutableStateFlow(initial)

    override val restrictions: StateFlow<UxrState> = mutableRestrictions.asStateFlow()

    fun set(value: UxrState) {
        mutableRestrictions.value = value
    }
}

class FakePackageChangeMonitor : PackageChangeMonitor {
    private val mutableChanges = MutableSharedFlow<PackageChange>(extraBufferCapacity = 16)

    override val packageChanges: Flow<PackageChange> = mutableChanges.asSharedFlow()

    fun emit(change: PackageChange) {
        check(mutableChanges.tryEmit(change)) { "Fake package change buffer is full" }
    }
}
