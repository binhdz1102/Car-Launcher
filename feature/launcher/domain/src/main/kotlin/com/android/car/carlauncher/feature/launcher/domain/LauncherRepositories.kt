package com.android.car.carlauncher.feature.launcher.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LauncherAppsRepository {
    val launchableApps: Flow<List<LaunchableApp>>
    val restrictions: StateFlow<LauncherRestrictions>

    suspend fun navigationTarget(): Result<EmbeddedAppTarget>

    suspend fun launch(app: LaunchableApp): Result<Unit>

    suspend fun saveOrderedComponents(componentNames: List<String>)

    suspend fun clearOrderedComponents()
}

interface RecentTasksRepository {
    val tasks: StateFlow<List<RecentTask>>

    suspend fun refresh(displayId: Int)

    suspend fun open(task: RecentTask): Result<Unit>

    suspend fun openTopRunningTask(displayId: Int): Result<Unit>

    suspend fun remove(task: RecentTask): Result<Unit>

    suspend fun clearAll(): Result<Unit>
}
