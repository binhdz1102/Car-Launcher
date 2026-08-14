package com.android.car.carlauncher.feature.appgrid.domain

import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.model.LauncherComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGridStateReducerTest {
    @Test
    fun arrange_keepsSavedOrderThenAlphabetical() {
        val media = item("media", AppGridItemType.MEDIA_SERVICE, recent = false)
        val recent = item("recent", AppGridItemType.ACTIVITY, recent = true)
        val alpha = item("alpha", AppGridItemType.ACTIVITY, recent = false)

        val result = AppGridStateReducer.arrange(listOf(alpha, recent, media), listOf(media.component.flattened))

        assertEquals(listOf(media, alpha, recent), result)
    }

    @Test
    fun filter_respectsModeAndSearch() {
        val activity = item("Maps", AppGridItemType.ACTIVITY, recent = false)
        val media = item("Music", AppGridItemType.MEDIA_SERVICE, recent = false)

        assertEquals(
            listOf(media),
            AppGridStateReducer.filter(listOf(activity, media), AppGridMode.MEDIA_ONLY, "mu"),
        )
    }

    @Test
    fun recentItemsUsesSameModeClassificationAsTheMainGrid() {
        val recentActivity = item("Maps", AppGridItemType.ACTIVITY, recent = true)
        val recentMedia = item("Music", AppGridItemType.MEDIA_SERVICE, recent = true)
        assertEquals(
            listOf(recentMedia),
            AppGridStateReducer.recentItems(listOf(recentActivity, recentMedia), AppGridMode.MEDIA_ONLY),
        )
    }

    @Test
    fun reorderOnlyAllowedWhileParkedWithUnfilteredAllApps() {
        assertTrue(AppGridStateReducer.canReorder(DrivingRestriction.UNRESTRICTED, "", AppGridMode.ALL_APPS))
        assertFalse(AppGridStateReducer.canReorder(DrivingRestriction.NO_KEYBOARD, "", AppGridMode.ALL_APPS))
        assertFalse(AppGridStateReducer.canReorder(DrivingRestriction.UNRESTRICTED, "maps", AppGridMode.ALL_APPS))
        assertFalse(AppGridStateReducer.canReorder(DrivingRestriction.UNRESTRICTED, "", AppGridMode.MEDIA_ONLY))
    }

    private fun item(
        label: String,
        type: AppGridItemType,
        recent: Boolean,
    ) = AppGridItem(
        component = LauncherComponent("test.$label", "test.$label.Main", 10),
        label = label,
        type = type,
        isRecent = recent,
        isDistractionOptimized = true,
        availability = AppGridAvailability.AVAILABLE,
    )
}
