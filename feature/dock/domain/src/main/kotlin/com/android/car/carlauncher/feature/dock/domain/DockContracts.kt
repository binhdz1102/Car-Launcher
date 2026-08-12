package com.android.car.carlauncher.feature.dock.domain

import com.android.car.carlauncher.core.model.LauncherComponent
import kotlinx.coroutines.flow.Flow

data class DockItem(
    val component: LauncherComponent,
    val position: Int,
    val isMediaApp: Boolean,
)

interface DockRepository {
    val items: Flow<List<DockItem>>

    suspend fun pin(
        component: LauncherComponent,
        position: Int,
    ): Result<Unit>

    suspend fun unpin(component: LauncherComponent): Result<Unit>
}
