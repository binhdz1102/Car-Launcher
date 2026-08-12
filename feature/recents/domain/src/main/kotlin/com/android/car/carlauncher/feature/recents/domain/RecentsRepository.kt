package com.android.car.carlauncher.feature.recents.domain

import com.android.car.carlauncher.core.model.LauncherTask
import kotlinx.coroutines.flow.Flow

interface RecentsRepository {
    val tasks: Flow<List<LauncherTask>>

    suspend fun launch(taskId: Int): Result<Unit>

    suspend fun dismiss(taskId: Int): Result<Unit>
}
