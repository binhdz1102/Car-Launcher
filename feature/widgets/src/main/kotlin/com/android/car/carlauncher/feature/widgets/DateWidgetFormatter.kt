package com.android.car.carlauncher.feature.widgets

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DateWidgetText(
    val dayOfWeek: String,
    val date: String,
)

/** Deterministic locale/time-zone formatting for the launcher Date widget. */
object DateWidgetFormatter {
    fun format(
        instant: Instant,
        zoneId: ZoneId,
        locale: Locale,
    ): DateWidgetText {
        val dateTime = instant.atZone(zoneId)
        return DateWidgetText(
            dayOfWeek = DateTimeFormatter.ofPattern("EEEE", locale).format(dateTime),
            date = DateTimeFormatter.ofPattern("MMMM d", locale).format(dateTime),
        )
    }
}
