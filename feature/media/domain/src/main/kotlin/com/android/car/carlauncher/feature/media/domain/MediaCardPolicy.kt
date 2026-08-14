package com.android.car.carlauncher.feature.media.domain

/**
 * Pure policy shared by the compact and fullscreen home-card presenters.
 *
 * The AOSP presenter gives an active call ownership of the audio card, then gives an active
 * projection ownership of the assistive slot. Keeping this decision framework-free makes the
 * ordering deterministic in unit tests and prevents a callback race from changing the UI.
 */
object MediaCardPolicy {
    fun visibleVariant(
        hasActivePlayback: Boolean,
        fullscreenEnabled: Boolean,
    ): MediaCardVariant =
        when {
            !hasActivePlayback -> MediaCardVariant.EMPTY
            fullscreenEnabled -> MediaCardVariant.FULLSCREEN
            else -> MediaCardVariant.COMPACT
        }

    fun assistiveCard(
        projection: ProjectionCard?,
        fallbacks: List<AssistiveCard>,
    ): AssistiveCard? =
        projection?.let { projected ->
            AssistiveCard(
                id = "projection:${projected.packageName}",
                priority = Int.MIN_VALUE,
                title = projected.appLabel,
                body = "Projected phone",
                footer = projected.statusMessage,
                launchIntentUri = projected.launchIntentUri,
                deviceCount = projected.deviceCount,
            )
        } ?: fallbacks.minWithOrNull(compareBy(AssistiveCard::priority, AssistiveCard::id))
}

enum class MediaCardVariant {
    EMPTY,
    COMPACT,
    FULLSCREEN,
}

/** Formats elapsed call time the same way the stock card does without Android dependencies. */
object CallDurationFormatter {
    fun format(elapsedRealtimeMs: Long?): String? {
        val elapsed = elapsedRealtimeMs?.takeIf { it >= 0L } ?: return null
        val totalSeconds = elapsed / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
