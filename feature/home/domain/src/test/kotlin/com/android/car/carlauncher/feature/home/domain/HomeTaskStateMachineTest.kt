package com.android.car.carlauncher.feature.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTaskStateMachineTest {
    private val navigation =
        HomeEmbeddedTaskTarget(
            componentName = "com.android.car.mapsplaceholder/.MapsPlaceholderActivity",
            label = "Navigation",
            type = HomeEmbeddedTargetType.NAVIGATION,
        )
    private val app =
        HomeEmbeddedTaskTarget(
            componentName = "com.android.calendar/.CalendarActivity",
            label = "Calendar",
            type = HomeEmbeddedTargetType.APPLICATION,
        )

    @Test
    fun selectionAndAppearanceTransitionToRunningContentMode() {
        val machine = HomeTaskStateMachine()

        assertTrue(machine.start(navigation) is HomeEmbeddedTaskState.Loading)
        assertTrue(machine.appeared(navigation.componentName) is HomeEmbeddedTaskState.Running)
        assertTrue(machine.select(app) is HomeEmbeddedTaskState.Loading)
        assertEquals(HomeTaskPaneMode.EMBEDDED_APP, machine.currentMode())
        assertTrue(machine.appeared(app.componentName) is HomeEmbeddedTaskState.Running)
    }

    @Test
    fun mismatchAndTimeoutKeepDiagnosticComponent() {
        val machine = HomeTaskStateMachine()
        machine.start(app)

        assertTrue(machine.appeared("com.example/.WrongActivity") is HomeEmbeddedTaskState.Error)
        val timeout = machine.failed("Timeout", "The task did not appear") as HomeEmbeddedTaskState.Error

        assertEquals(app.componentName, timeout.error.componentName)
        assertEquals(HomeTaskPaneMode.ERROR, machine.currentMode())
    }
}
