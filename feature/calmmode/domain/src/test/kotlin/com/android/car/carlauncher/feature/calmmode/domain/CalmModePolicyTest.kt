package com.android.car.carlauncher.feature.calmmode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CalmModePolicyTest {
    @Test
    fun visibility_featureDisabled_hidesEverySurface() {
        val visibility = CalmModePolicy.visibility(true, true, true, true, true, true)
        val disabled = CalmModePolicy.visibility(false, true, true, true, true, true)

        assertTrue(visibility.clock)
        assertFalse(disabled.clock)
        assertFalse(disabled.date)
        assertFalse(disabled.media)
        assertFalse(disabled.navigation)
        assertFalse(disabled.temperature)
    }

    @Test
    fun formatTemperature_localizesAndKeepsUnit() {
        assertEquals(
            "21\u00b0C",
            CalmModePolicy.formatTemperature(CalmTemperature(20.6f, TemperatureUnit.CELSIUS), Locale.US),
        )
        assertEquals(
            "70\u00b0F",
            CalmModePolicy.formatTemperature(CalmTemperature(69.6f, TemperatureUnit.FAHRENHEIT), Locale.US),
        )
    }

    @Test
    fun formatMediaTitle_requiresTitleAndAddsArtistWhenPresent() {
        assertNull(CalmModePolicy.formatMediaTitle(" ", "Artist", " • "))
        assertEquals("Song • Artist", CalmModePolicy.formatMediaTitle("Song", "Artist", " • "))
        assertEquals("Song", CalmModePolicy.formatMediaTitle("Song", "", " • "))
    }
}
