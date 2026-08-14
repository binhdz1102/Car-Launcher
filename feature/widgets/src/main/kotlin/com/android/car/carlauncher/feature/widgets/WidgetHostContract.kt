package com.android.car.carlauncher.feature.widgets

import com.android.car.carlauncher.core.model.DisplayTarget
import kotlinx.coroutines.flow.StateFlow

data class WidgetHostState(
    val appWidgetId: Int?,
    val display: DisplayTarget,
    val isBound: Boolean,
    val providerCount: Int = 0,
)

/** State transition rules used when the Activity is stopped and later rebound. */
object WidgetHostLifecycleReducer {
    fun stopped(state: WidgetHostState): WidgetHostState = state.copy(isBound = false)

    fun started(
        state: WidgetHostState,
        display: DisplayTarget,
    ): WidgetHostState = state.copy(display = display)
}

/** Boundary for AppWidgetHost side effects, kept out of the HOME presenter. */
interface WidgetHostController {
    val state: StateFlow<WidgetHostState>

    suspend fun bind(display: DisplayTarget): Result<WidgetHostState>

    suspend fun unbind(): Result<Unit>
}
