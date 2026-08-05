package com.android.car.carlauncher.feature.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPaneStateMachineTest {
    private val navigation = EmbeddedAppTarget(
        componentName = "com.android.car.mapsplaceholder/.MapsPlaceholderActivity",
        label = "Navigation",
        type = EmbeddedTargetType.NAVIGATION,
    )
    private val app = EmbeddedAppTarget(
        componentName = "com.android.calendar/.CalendarActivity",
        label = "Calendar",
        type = EmbeddedTargetType.APPLICATION,
    )

    @Test
    fun navigationAndApplicationTransitionsKeepSelectedTarget() {
        val machine = LauncherPaneStateMachine()

        assertTrue(machine.start(navigation) is EmbeddedTaskState.Loading)
        assertTrue(machine.appeared(navigation.componentName) is EmbeddedTaskState.Running)
        assertTrue(machine.select(app) is EmbeddedTaskState.Loading)
        assertEquals(LauncherPaneMode.EMBEDDED_APP, machine.currentMode())
        assertTrue(machine.appeared(app.componentName) is EmbeddedTaskState.Running)
    }

    @Test
    fun unexpectedTaskIsReportedAsError() {
        val machine = LauncherPaneStateMachine()
        machine.start(navigation)

        val error = machine.appeared("com.example/.WrongActivity")

        assertTrue(error is EmbeddedTaskState.Error)
        assertEquals(LauncherPaneMode.ERROR, machine.currentMode())
    }
}
