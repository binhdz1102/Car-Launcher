package com.android.car.carlauncher.feature.widgets

import com.android.car.carlauncher.core.model.DisplayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHostLifecycleReducerTest {
    private val display = DisplayTarget(displayId = 2, isDefaultDisplay = false)

    @Test
    fun stopped_keepsAllocatedWidgetIdForRebind() {
        val state = WidgetHostState(appWidgetId = 42, display = display, isBound = true, providerCount = 1)

        val stopped = WidgetHostLifecycleReducer.stopped(state)

        assertFalse(stopped.isBound)
        assertEquals(42, stopped.appWidgetId)
    }

    @Test
    fun started_updatesDisplayWithoutDroppingWidget() {
        val state = WidgetHostState(appWidgetId = 42, display = DisplayTarget(0, true), isBound = false)

        val started = WidgetHostLifecycleReducer.started(state, display)

        assertEquals(display, started.display)
        assertEquals(42, started.appWidgetId)
        assertTrue(!started.isBound)
    }
}
