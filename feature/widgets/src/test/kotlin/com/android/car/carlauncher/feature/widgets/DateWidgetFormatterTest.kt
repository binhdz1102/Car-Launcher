package com.android.car.carlauncher.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class DateWidgetFormatterTest {
    @Test
    fun format_usesLocaleAndTimeZone() {
        val text =
            DateWidgetFormatter.format(
                instant = Instant.parse("2026-08-14T00:30:00Z"),
                zoneId = ZoneId.of("America/Los_Angeles"),
                locale = Locale.US,
            )

        assertEquals("Thursday", text.dayOfWeek)
        assertEquals("August 13", text.date)
    }
}
