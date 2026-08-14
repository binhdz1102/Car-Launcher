package com.android.car.carlauncher.feature.media.data

import android.media.session.PlaybackState

/**
 * Mirrors the stock media-common rule: stopped/error sessions are not primary playback sources.
 * Metadata and queue presence break ties between active or paused sessions.
 */
internal object MediaControllerSelection {
    private const val STATE_SCORE_WEIGHT = 10

    fun isEligible(state: Int?): Boolean = score(state, false, false) > 0

    fun score(
        state: Int?,
        hasMetadata: Boolean,
        hasQueue: Boolean,
    ): Int {
        val stateScore =
            when (state) {
                PlaybackState.STATE_PLAYING -> 3
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_CONNECTING,
                PlaybackState.STATE_FAST_FORWARDING,
                PlaybackState.STATE_REWINDING,
                -> 2
                else -> 0
            }
        return stateScore * STATE_SCORE_WEIGHT +
            (if (hasMetadata) 2 else 0) +
            (if (hasQueue) 1 else 0)
    }
}
