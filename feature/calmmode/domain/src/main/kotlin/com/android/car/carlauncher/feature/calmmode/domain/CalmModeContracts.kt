package com.android.car.carlauncher.feature.calmmode.domain

import kotlinx.coroutines.flow.Flow

data class CalmModeState(
    val enabled: Boolean,
    val title: String,
)

interface CalmModeRepository {
    val state: Flow<CalmModeState>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}
