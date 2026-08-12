package com.android.car.carlauncher.feature.appgrid.domain

import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.model.LauncherComponent
import kotlinx.coroutines.flow.Flow

enum class AppGridOrientation {
    HORIZONTAL,
    VERTICAL,
}

data class AppGridItem(
    val component: LauncherComponent,
    val label: String,
    val isRecent: Boolean,
    val isDisabled: Boolean,
)

data class AppGridState(
    val items: List<AppGridItem>,
    val orientation: AppGridOrientation,
    val restriction: DrivingRestriction,
    val tosAccepted: Boolean,
)

interface AppGridRepository {
    val state: Flow<AppGridState>

    suspend fun reorder(
        fromIndex: Int,
        toIndex: Int,
    ): Result<Unit>

    suspend fun launch(item: AppGridItem): Result<Unit>
}
