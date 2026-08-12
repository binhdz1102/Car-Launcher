package com.android.car.carlauncher.feature.home.domain

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.core.model.LauncherTask
import kotlinx.coroutines.flow.Flow

enum class HomePane {
    MAP,
    MEDIA,
}

data class HomePaneState(
    val pane: HomePane,
    val task: LauncherTask?,
    val targetComponent: LauncherComponent?,
    val display: DisplayTarget,
)

interface HomeTaskRepository {
    val panes: Flow<List<HomePaneState>>

    suspend fun requestEmbed(pane: HomePane): Result<Unit>

    suspend fun removeTask(pane: HomePane): Result<Unit>
}
