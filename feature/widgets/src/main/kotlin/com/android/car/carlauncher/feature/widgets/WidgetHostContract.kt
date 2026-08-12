package com.android.car.carlauncher.feature.widgets

import com.android.car.carlauncher.core.model.DisplayTarget

data class WidgetHostState(
    val appWidgetId: Int?,
    val display: DisplayTarget,
    val isBound: Boolean,
)

/** Boundary for AppWidgetHost side effects, kept out of the HOME presenter. */
interface WidgetHostController {
    suspend fun bind(display: DisplayTarget): Result<WidgetHostState>

    suspend fun unbind(): Result<Unit>
}
