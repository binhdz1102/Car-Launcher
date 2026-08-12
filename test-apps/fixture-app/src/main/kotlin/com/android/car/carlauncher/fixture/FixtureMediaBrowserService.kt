package com.android.car.carlauncher.fixture

import android.content.Intent
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService

/**
 * A small legacy MediaBrowserService with an active MediaSession. It gives the launcher a
 * reproducible source, queue, playback controls, and metadata without depending on a third-party
 * media application.
 */
class FixtureMediaBrowserService : MediaBrowserService() {
    private lateinit var session: MediaSession
    private var currentIndex = 0
    private var isPlaying = false
    private var positionMs = 0L

    override fun onCreate() {
        super.onCreate()
        session =
            MediaSession(this, SESSION_TAG).apply {
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() = updatePlayback(true)

                        override fun onPause() = updatePlayback(false)

                        override fun onSkipToNext() {
                            currentIndex = (currentIndex + 1) % TITLES.size
                            positionMs = 0L
                            publishState()
                        }

                        override fun onSkipToPrevious() {
                            currentIndex = (currentIndex - 1 + TITLES.size) % TITLES.size
                            positionMs = 0L
                            publishState()
                        }

                        override fun onSeekTo(position: Long) {
                            positionMs = position.coerceIn(0L, TRACK_DURATION_MS)
                            publishState()
                        }
                    },
                )
                setQueue(
                    TITLES.mapIndexed { index, title ->
                        MediaSession.QueueItem(
                            MediaDescription
                                .Builder()
                                .setMediaId(index.toString())
                                .setTitle(title)
                                .setSubtitle(ARTIST)
                                .build(),
                            index.toLong(),
                        )
                    },
                )
                isActive = true
            }
        sessionToken = session.sessionToken
        publishState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            FixtureContract.ACTION_MEDIA_PLAY -> updatePlayback(true)
            FixtureContract.ACTION_MEDIA_PAUSE -> updatePlayback(false)
            FixtureContract.ACTION_MEDIA_NEXT -> session.controller.transportControls.skipToNext()
            FixtureContract.ACTION_MEDIA_PREVIOUS -> session.controller.transportControls.skipToPrevious()
            FixtureContract.ACTION_MEDIA_RESET -> {
                currentIndex = 0
                positionMs = 0L
                updatePlayback(false)
            }
        }
        return START_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>,
    ) {
        val children =
            TITLES
                .mapIndexed { index, title ->
                    MediaBrowser.MediaItem(
                        MediaDescription
                            .Builder()
                            .setMediaId(index.toString())
                            .setTitle(title)
                            .setSubtitle(ARTIST)
                            .build(),
                        MediaBrowser.MediaItem.FLAG_PLAYABLE,
                    )
                }.toMutableList()
        result.sendResult(children)
    }

    override fun onDestroy() {
        session.release()
        super.onDestroy()
    }

    private fun updatePlayback(playing: Boolean) {
        isPlaying = playing
        publishState()
    }

    private fun publishState() {
        session.setMetadata(
            MediaMetadata
                .Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, TITLES[currentIndex])
                .putString(MediaMetadata.METADATA_KEY_ARTIST, ARTIST)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, TRACK_DURATION_MS)
                .build(),
        )
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState
                .Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO,
                ).setState(state, positionMs, 1f, SystemClock.elapsedRealtime())
                .build(),
        )
    }

    private companion object {
        const val SESSION_TAG = "CarLauncherFixture"
        const val ROOT_ID = "fixture-root"
        const val ARTIST = "Car Launcher Fixture"
        const val TRACK_DURATION_MS = 180_000L
        val TITLES = listOf("Fixture Drive", "Fixture Arrival", "Fixture Park")
    }
}
