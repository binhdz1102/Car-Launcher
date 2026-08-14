package com.android.car.carlauncher.feature.media.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaCardPolicyTest {
    @Test
    fun disabledFullscreenUsesCompactCard() {
        assertEquals(
            MediaCardVariant.COMPACT,
            MediaCardPolicy.visibleVariant(hasActivePlayback = true, fullscreenEnabled = false),
        )
    }

    @Test
    fun noPlaybackHidesMediaCard() {
        assertEquals(
            MediaCardVariant.EMPTY,
            MediaCardPolicy.visibleVariant(hasActivePlayback = false, fullscreenEnabled = true),
        )
    }

    @Test
    fun projectionWinsAndFallbackIsStableById() {
        val fallback =
            listOf(
                AssistiveCard("z", 10, "Z", "z"),
                AssistiveCard("a", 10, "A", "a"),
            )
        assertEquals("a", MediaCardPolicy.assistiveCard(null, fallback)?.id)
        assertNull(MediaCardPolicy.assistiveCard(null, emptyList()))
        assertEquals(
            "projection:phone",
            MediaCardPolicy
                .assistiveCard(
                    ProjectionCard("phone", "Android Auto"),
                    fallback,
                )?.id,
        )
    }

    @Test
    fun durationFormatterUsesHoursOnlyWhenNeeded() {
        assertEquals("01:05", CallDurationFormatter.format(65_000L))
        assertEquals("1:01:05", CallDurationFormatter.format(3_665_000L))
        assertNull(CallDurationFormatter.format(null))
    }
}
