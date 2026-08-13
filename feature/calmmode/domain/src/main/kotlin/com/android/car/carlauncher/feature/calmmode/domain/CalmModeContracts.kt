package com.android.car.carlauncher.feature.calmmode.domain

import kotlinx.coroutines.flow.Flow

data class CalmModeState(
    val enabled: Boolean,
    val title: String,
)

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
}

data class CalmTemperature(
    val value: Float,
    val unit: TemperatureUnit,
)

interface TemperatureRepository {
    val temperature: Flow<CalmTemperature?>
}

interface CalmModeRepository {
    val state: Flow<CalmModeState>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}
