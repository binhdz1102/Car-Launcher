package com.android.car.carlauncher.feature.launcher.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LauncherAppsRepository {
    val launchableApps: Flow<List<LaunchableApp>>
    val restrictions: StateFlow<LauncherRestrictions>

    suspend fun navigationTarget(): Result<EmbeddedAppTarget>

    suspend fun launch(app: LaunchableApp): Result<Unit>
}
