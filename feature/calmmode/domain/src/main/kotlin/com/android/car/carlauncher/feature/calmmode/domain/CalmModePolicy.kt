package com.android.car.carlauncher.feature.calmmode.domain

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Pure presentation rules shared by the Calm Mode activity and deterministic tests. */
object CalmModePolicy {
    fun visibility(
        featureEnabled: Boolean,
        showClock: Boolean,
        showDate: Boolean,
        showMedia: Boolean,
        showNavigation: Boolean,
        showTemperature: Boolean,
    ): CalmModeVisibility =
        CalmModeVisibility(
            clock = featureEnabled && showClock,
            date = featureEnabled && showDate,
            media = featureEnabled && showMedia,
            navigation = featureEnabled && showNavigation,
            temperature = featureEnabled && showTemperature,
        )

    fun formatTemperature(
        temperature: CalmTemperature,
        locale: Locale,
    ): String {
        val formatter =
            NumberFormat.getIntegerInstance(locale).apply {
                roundingMode = RoundingMode.HALF_UP
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            }
        val suffix =
            when (temperature.unit) {
                TemperatureUnit.CELSIUS -> "\u00b0C"
                TemperatureUnit.FAHRENHEIT -> "\u00b0F"
            }
        return formatter.format(temperature.value) + suffix
    }

    fun formatMediaTitle(
        title: String?,
        artist: String?,
        separator: String,
    ): String? {
        val cleanTitle = title?.takeIf(String::isNotBlank) ?: return null
        val cleanArtist = artist?.takeIf(String::isNotBlank)
        return cleanArtist?.let { "$cleanTitle$separator$it" } ?: cleanTitle
    }
}

data class CalmModeVisibility(
    val clock: Boolean,
    val date: Boolean,
    val media: Boolean,
    val navigation: Boolean,
    val temperature: Boolean,
)
