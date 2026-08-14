package com.android.car.carlauncher.feature.media.data

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerSelectionTest {
    @Test
    fun stoppedAndErrorSessionsAreNotEligible() {
        assertFalse(MediaControllerSelection.isEligible(PlaybackState.STATE_STOPPED))
        assertFalse(MediaControllerSelection.isEligible(PlaybackState.STATE_ERROR))
        assertFalse(MediaControllerSelection.isEligible(null))
    }

    @Test
    fun playingSourceBeatsPausedSource() {
        assertTrue(
            MediaControllerSelection.score(PlaybackState.STATE_PLAYING, false, false) >
                MediaControllerSelection.score(PlaybackState.STATE_PAUSED, true, true),
        )
    }

    @Test
    fun metadataAndQueueBreakPausedTie() {
        assertTrue(
            MediaControllerSelection.score(PlaybackState.STATE_PAUSED, true, true) >
                MediaControllerSelection.score(PlaybackState.STATE_PAUSED, false, false),
        )
    }
}
