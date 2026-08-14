package com.android.car.carlauncher.feature.recents.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsStateReducerTest {
    @Test
    fun stableTasks_keepsFirstPlatformRecordForTaskId() {
        val first = task(taskId = 1, label = "Maps")
        val duplicate = task(taskId = 1, label = "Maps duplicate")

        assertEquals(listOf(first), RecentsStateReducer.stableTasks(listOf(first, duplicate)))
    }

    @Test
    fun canDismissToTask_rejectsMissingAndRecentsTask() {
        val recents = "com.android.car.carlauncher/.recents.CarRecentsActivity"

        assertFalse(RecentsStateReducer.canDismissToTask(null, recents))
        assertFalse(RecentsStateReducer.canDismissToTask(recents, recents))
        assertTrue(RecentsStateReducer.canDismissToTask("com.example.maps/.MapsActivity", recents))
    }

    @Test
    fun orderedForDisplay_deduplicatesAndKeepsPlatformOrder() {
        val first = task(taskId = 1, label = "Maps").copy(displayId = 1)
        val duplicate = task(taskId = 1, label = "stale").copy(displayId = 1)
        val otherDisplay = task(taskId = 2, label = "Music").copy(displayId = 0)

        assertEquals(listOf(first), RecentsStateReducer.orderedForDisplay(listOf(first, duplicate, otherDisplay), 1))
    }

    private fun task(
        taskId: Int,
        label: String,
    ) = RecentTask(
        taskId = taskId,
        componentName = "com.example/$label",
        packageName = "com.example",
        label = label,
    )
}
