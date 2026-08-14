package com.android.car.carlauncher.feature.dock.domain

import com.android.car.carlauncher.core.model.LauncherComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockPolicyTest {
    private val first = LauncherComponent("pkg.one", "Activity", userId = 0)
    private val second = LauncherComponent("pkg.two", "Activity", userId = 0)

    @Test
    fun pin_movesExistingComponentToRequestedPosition() {
        assertEquals(listOf(second, first), DockPolicy.pin(listOf(first, second), second, 0))
    }

    @Test
    fun dynamicReplacement_prefersExistingPackageThenLeastRecent() {
        val items =
            listOf(
                DockItem(first, 0, false, kind = DockItemKind.STATIC),
                DockItem(second, 1, false, kind = DockItemKind.DYNAMIC),
            )
        assertEquals(0, DockPolicy.dynamicReplacementIndex(items, first, 4))
        assertEquals(
            1,
            DockPolicy.dynamicReplacementIndex(
                items,
                LauncherComponent("pkg.three", "Activity", userId = 0),
                4,
            ),
        )
    }

    @Test
    fun dynamicReplacement_returnsEmptySlotOrNullWhenFullStatic() {
        val items = listOf(DockItem(first, 0, false, kind = DockItemKind.STATIC))
        assertEquals(1, DockPolicy.dynamicReplacementIndex(items, second, 4))
        assertNull(DockPolicy.dynamicReplacementIndex(List(2) { items.first() }, second, 2))
    }

    @Test
    fun canLaunch_allowsMediaDuringUxR() {
        val restricted = DockItem(first, 0, false, isDistractionOptimized = false)
        val media = restricted.copy(isMediaApp = true, kind = DockItemKind.MEDIA)
        assertFalse(DockPolicy.canLaunch(restricted, true))
        assertTrue(DockPolicy.canLaunch(media, true))
    }
}
