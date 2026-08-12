package com.android.car.carlauncher.feature.launcher.data

import android.car.media.CarMediaIntents
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaQueueItem
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
    ) : MediaRepository {
        private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
        private var carMediaManager: CarMediaManager? = null
        private val _playback = MutableStateFlow(MediaPlayback())
        private val _sources = MutableStateFlow(emptyList<MediaSource>())
        private val _queue = MutableStateFlow(emptyList<MediaQueueItem>())
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var controller: MediaController? = null
        private var progressJob: Job? = null

        override val playback: StateFlow<MediaPlayback> = _playback.asStateFlow()
        override val sources: StateFlow<List<MediaSource>> = _sources.asStateFlow()
        override val queue: StateFlow<List<MediaQueueItem>> = _queue.asStateFlow()

        private val controllerCallback =
            object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = refreshPlayback()

                override fun onPlaybackStateChanged(state: PlaybackState?) = refreshPlayback()

                override fun onSessionDestroyed() {
                    controller = null
                    chooseActiveController()
                }
            }

        private val sessionsChanged =
            MediaSessionManager.OnActiveSessionsChangedListener { chooseActiveController() }
        private val mediaSourceChanged =
            CarMediaManager.MediaSourceChangedListener {
                refreshSources()
            }

        init {
            runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, null) }
                .onFailure { Timber.tag(TAG).w(it, "Media session listener unavailable") }
            repositoryScope.launch {
                carConnection.car.collectLatest { car ->
                    carMediaManager?.let { previous ->
                        runCatching {
                            previous.removeMediaSourceListener(
                                mediaSourceChanged,
                                CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK,
                            )
                        }
                    }
                    carMediaManager =
                        car?.let {
                            runCatching { it.getCarManager(CarMediaManager::class.java) }.getOrNull()
                        }
                    runCatching {
                        carMediaManager?.addMediaSourceListener(
                            mediaSourceChanged,
                            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK,
                        )
                    }.onFailure { Timber.tag(TAG).w(it, "Car media source listener unavailable") }
                    refreshSources()
                }
            }
            chooseActiveController()
        }

        override fun playPause() {
            runCatching {
                val controls = controller?.transportControls ?: return
                if (_playback.value.isPlaying) controls.pause() else controls.play()
            }.onFailure { Timber.tag(TAG).w(it, "Unable to toggle media playback") }
        }

        override fun previous() {
            runCatching { controller?.transportControls?.skipToPrevious() }
                .onFailure { Timber.tag(TAG).w(it, "Unable to skip to previous media item") }
        }

        override fun next() {
            runCatching { controller?.transportControls?.skipToNext() }
                .onFailure { Timber.tag(TAG).w(it, "Unable to skip to next media item") }
        }

        override fun seekTo(positionMs: Long) {
            runCatching { controller?.transportControls?.seekTo(positionMs) }
                .onFailure { Timber.tag(TAG).w(it, "Unable to seek media position=%d", positionMs) }
        }

        override fun selectSource(source: MediaSource) {
            val component = ComponentName.unflattenFromString(source.componentName) ?: return
            runCatching {
                checkNotNull(carMediaManager) { "Car media manager is unavailable" }
                    .setMediaSource(component, CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                repositoryScope.launch {
                    delay(SOURCE_SWITCH_REFRESH_DELAY_MS)
                    refreshSources()
                    chooseActiveController()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Unable to select media source") }
        }

        override fun openMediaCenter() {
            val componentName = _playback.value.sourceComponent ?: return
            val intent =
                Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
                    .putExtra(CarMediaIntents.EXTRA_MEDIA_COMPONENT, componentName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.tag(TAG).w(it, "Unable to open media center") }
        }

        private fun chooseActiveController() {
            val next =
                runCatching {
                    sessionManager
                        .getActiveSessions(null)
                        .sortedByDescending { active ->
                            if (active.playbackState?.state == PlaybackState.STATE_PLAYING) 1 else 0
                        }.firstOrNull()
                }.getOrNull()
            if (next?.sessionToken == controller?.sessionToken) {
                refreshPlayback()
                return
            }
            controller?.unregisterCallback(controllerCallback)
            controller = next
            next?.registerCallback(controllerCallback)
            refreshPlayback()
            Timber.tag(TAG).d("Selected active media controller=%s", next?.packageName)
        }

        private fun refreshSources() {
            val manager = carMediaManager
            val currentSource =
                runCatching {
                    manager?.getMediaSource(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                }.getOrNull()
            _sources.value =
                runCatching {
                    (
                        listOfNotNull(currentSource) +
                            manager?.getLastMediaSources(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK).orEmpty()
                    ).distinct()
                        .map { component ->
                            val label =
                                runCatching {
                                    context.packageManager
                                        .getApplicationLabel(
                                            context.packageManager.getApplicationInfo(component.packageName, 0),
                                        ).toString()
                                }.getOrDefault(component.packageName)
                            MediaSource(component.flattenToString(), label)
                        }
                }.getOrDefault(emptyList())
            if (controller == null && currentSource != null) {
                _playback.value =
                    MediaPlayback(
                        sourcePackage = currentSource.packageName,
                        sourceComponent = currentSource.flattenToString(),
                        sourceLabel = context.applicationLabel(currentSource.packageName),
                    )
            }
        }

        private fun refreshPlayback() {
            val active = controller
            if (active == null) {
                progressJob?.cancel()
                _playback.value = MediaPlayback()
                _queue.value = emptyList()
                return
            }
            val metadata = active.metadata
            val state = active.playbackState
            val actions = state?.actions ?: 0L
            _playback.value =
                MediaPlayback(
                    sourcePackage = active.packageName,
                    sourceComponent = sourceComponentFor(active.packageName),
                    sourceLabel = context.applicationLabel(active.packageName),
                    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    artworkBytes =
                        (
                            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        )?.toArtworkBytes(),
                    isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                    positionMs = state?.position ?: 0L,
                    durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                    canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                    canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
                )
            _queue.value =
                active.queue.orEmpty().map { item ->
                    MediaQueueItem(
                        id = item.queueId,
                        title =
                            item.description.title
                                ?.toString()
                                .orEmpty(),
                        subtitle =
                            item.description.subtitle
                                ?.toString()
                                .orEmpty(),
                    )
                }
            updateProgressLoop()
        }

        /** Media callbacks do not guarantee periodic position updates; Flow consumers still need a
         * smooth seek bar while the session is playing. */
        private fun updateProgressLoop() {
            progressJob?.cancel()
            val active = controller ?: return
            if (active.playbackState?.state != PlaybackState.STATE_PLAYING) return
            progressJob =
                repositoryScope.launch {
                    var state = active.playbackState
                    while (
                        isActive &&
                        controller?.sessionToken == active.sessionToken &&
                        state?.state == PlaybackState.STATE_PLAYING
                    ) {
                        val playingState = checkNotNull(state)
                        val elapsedMs =
                            if (playingState.lastPositionUpdateTime > 0L) {
                                SystemClock.elapsedRealtime() - playingState.lastPositionUpdateTime
                            } else {
                                0L
                            }
                        val speed = playingState.playbackSpeed.takeUnless(Float::isNaN) ?: 1f
                        val positionMs =
                            (playingState.position + elapsedMs * speed)
                                .toLong()
                                .coerceAtLeast(0L)
                        _playback.update { playback ->
                            if (playback.sourcePackage == active.packageName) {
                                playback.copy(positionMs = positionMs)
                            } else {
                                playback
                            }
                        }
                        delay(PROGRESS_UPDATE_INTERVAL_MS)
                        state = active.playbackState
                    }
                }
        }

        private fun sourceComponentFor(packageName: String): String? =
            _sources.value
                .firstOrNull { source ->
                    ComponentName.unflattenFromString(source.componentName)?.packageName == packageName
                }?.componentName ?: runCatching {
                carMediaManager
                    ?.getMediaSource(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                    ?.takeIf { it.packageName == packageName }
                    ?.flattenToString()
            }.getOrNull()

        private companion object {
            const val TAG = "CarLauncher.MediaRepository"
            const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L
            const val SOURCE_SWITCH_REFRESH_DELAY_MS = 250L
        }
    }

private fun Context.applicationLabel(packageName: String): String =
    runCatching {
        packageManager
            .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
            .toString()
    }.getOrDefault(packageName)

private fun Bitmap.toArtworkBytes(): ByteArray? =
    runCatching {
        ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.PNG, ARTWORK_PNG_QUALITY, output)
            output.toByteArray()
        }
    }.getOrNull()

private const val ARTWORK_PNG_QUALITY = 100
