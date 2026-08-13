package com.android.car.carlauncher.feature.recents.domain

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.DrivingRestriction
import kotlinx.coroutines.flow.StateFlow

data class RecentTask(
    val taskId: Int,
    val componentName: String,
    val packageName: String,
    val label: String,
    val thumbnailBytes: ByteArray? = null,
    val restriction: DrivingRestriction = DrivingRestriction.UNRESTRICTED,
    val isEnabled: Boolean = true,
) {
    init {
        require(taskId >= 0) { "taskId must be non-negative" }
        require(componentName.isNotBlank()) { "componentName must not be blank" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

data class RecentsState(
    val tasks: List<RecentTask> = emptyList(),
    val display: DisplayTarget = DisplayTarget(displayId = 0, isDefaultDisplay = true),
    val isLoading: Boolean = true,
)

interface RecentsRepository {
    val state: StateFlow<RecentsState>

    suspend fun refresh(display: DisplayTarget)

    suspend fun launch(task: RecentTask): Result<Unit>

    suspend fun launchTopRunningTask(display: DisplayTarget): Result<Unit>

    suspend fun dismiss(task: RecentTask): Result<Unit>

    suspend fun clearAll(): Result<Unit>
}
