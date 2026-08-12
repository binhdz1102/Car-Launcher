package com.android.car.carlauncher.feature.media.data

import android.car.media.CarMediaIntents
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.feature.media.domain.MediaPlayback
import com.android.car.carlauncher.feature.media.domain.MediaQueueItem
import com.android.car.carlauncher.feature.media.domain.MediaRepository
import com.android.car.carlauncher.feature.media.domain.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AAOS media adapter. Android callbacks only enqueue events; all state mutation happens in the
 * structured repository scope and is exposed as StateFlow to Home card consumers.
 */
@Singleton
@Suppress("TooManyFunctions") // The platform media lifecycle belongs in one callback adapter.
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMediaRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
        private val artworkCache: MediaArtworkCache,
    ) : MediaRepository {
        private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val controllerEvents =
            MutableSharedFlow<ControllerEvent>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        private val mutablePlayback = MutableStateFlow(MediaPlayback())
        private val mutableSources = MutableStateFlow(emptyList<MediaSource>())
        private val mutableQueue = MutableStateFlow(emptyList<MediaQueueItem>())

        private var carMediaManager: CarMediaManager? = null
        private var controller: MediaController? = null
        private var progressJob: Job? = null

        override val playback: StateFlow<MediaPlayback> = mutablePlayback.asStateFlow()
        override val sources: StateFlow<List<MediaSource>> = mutableSources.asStateFlow()
        override val queue: StateFlow<List<MediaQueueItem>> = mutableQueue.asStateFlow()

        private val controllerCallback =
            object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    controllerEvents.tryEmit(ControllerEvent.Refresh)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    controllerEvents.tryEmit(ControllerEvent.Refresh)
                }

                override fun onSessionDestroyed() {
                    controllerEvents.tryEmit(ControllerEvent.Destroyed)
                }
            }

        init {
            repositoryScope.launch {
                activeSessionEvents().collect { chooseActiveController() }
            }
            repositoryScope.launch {
                carConnection.car
                    .flatMapLatest(::mediaSourceEvents)
                    .collectLatest { manager ->
                        carMediaManager = manager
                        refreshSources()
                        if (controller == null) chooseActiveController()
                    }
            }
            repositoryScope.launch {
                controllerEvents.collect { event ->
                    when (event) {
                        ControllerEvent.Refresh -> refreshPlayback()
                        ControllerEvent.Destroyed -> {
                            controller = null
                            chooseActiveController()
                        }
                    }
                }
            }
        }

        override fun playPause() {
            runCatching {
                val controls = controller?.transportControls ?: return
                if (mutablePlayback.value.isPlaying) controls.pause() else controls.play()
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
            val componentName = mutablePlayback.value.sourceComponent ?: return
            val intent =
                Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
                    .putExtra(CarMediaIntents.EXTRA_MEDIA_COMPONENT, componentName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.tag(TAG).w(it, "Unable to open media center") }
        }

        private fun activeSessionEvents(): Flow<Unit> =
            callbackFlow {
                val listener = MediaSessionManager.OnActiveSessionsChangedListener { trySend(Unit) }
                val registered =
                    runCatching {
                        sessionManager.addOnActiveSessionsChangedListener(listener, null)
                    }.onFailure { Timber.tag(TAG).w(it, "Media session listener unavailable") }
                        .isSuccess
                trySend(Unit)
                awaitClose {
                    if (registered) {
                        runCatching { sessionManager.removeOnActiveSessionsChangedListener(listener) }
                    }
                }
            }

        private fun mediaSourceEvents(car: android.car.Car?): Flow<CarMediaManager?> =
            callbackFlow {
                val manager =
                    car?.let { connectedCar ->
                        runCatching {
                            connectedCar.getCarManager(CarMediaManager::class.java)
                        }.onFailure { Timber.tag(TAG).w(it, "Car media manager unavailable") }
                            .getOrNull()
                    }
                if (manager == null) {
                    trySend(null)
                    awaitClose { }
                    return@callbackFlow
                }
                val listener = CarMediaManager.MediaSourceChangedListener { trySend(manager) }
                val registered =
                    runCatching {
                        manager.addMediaSourceListener(
                            listener,
                            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK,
                        )
                    }.onFailure { Timber.tag(TAG).w(it, "Car media source listener unavailable") }
                        .isSuccess
                trySend(manager)
                awaitClose {
                    if (registered) {
                        runCatching {
                            manager.removeMediaSourceListener(
                                listener,
                                CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK,
                            )
                        }
                    }
                }
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
            mutableSources.value =
                runCatching {
                    (
                        listOfNotNull(currentSource) +
                            manager?.getLastMediaSources(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK).orEmpty()
                    ).distinct()
                        .map { component ->
                            MediaSource(
                                componentName = component.flattenToString(),
                                label = context.applicationLabel(component.packageName),
                            )
                        }
                }.getOrDefault(emptyList())
            if (controller == null && currentSource != null) {
                mutablePlayback.value =
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
                mutablePlayback.value = MediaPlayback()
                mutableQueue.value = emptyList()
                return
            }
            val metadata = active.metadata
            val state = active.playbackState
            val actions = state?.actions ?: 0L
            mutablePlayback.value =
                MediaPlayback(
                    sourcePackage = active.packageName,
                    sourceComponent = sourceComponentFor(active.packageName),
                    sourceLabel = context.applicationLabel(active.packageName),
                    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    artwork =
                        artworkCache.encode(
                            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                        ),
                    isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                    positionMs = state?.position ?: 0L,
                    durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                    canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                    canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
                )
            mutableQueue.value =
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

        /** Media callbacks do not publish a position every tick; keep the seek bar smooth. */
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
                        mutablePlayback.update { current ->
                            if (current.sourcePackage == active.packageName) {
                                current.copy(positionMs = positionMs)
                            } else {
                                current
                            }
                        }
                        delay(PROGRESS_UPDATE_INTERVAL_MS)
                        state = active.playbackState
                    }
                }
        }

        private fun sourceComponentFor(packageName: String): String? =
            sources.value
                .firstOrNull { source ->
                    ComponentName.unflattenFromString(source.componentName)?.packageName == packageName
                }?.componentName ?: runCatching {
                carMediaManager
                    ?.getMediaSource(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                    ?.takeIf { it.packageName == packageName }
                    ?.flattenToString()
            }.getOrNull()

        private sealed interface ControllerEvent {
            data object Refresh : ControllerEvent

            data object Destroyed : ControllerEvent
        }

        private companion object {
            const val TAG = "CarLauncher.MediaRepository"
            val PROGRESS_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1)
            const val SOURCE_SWITCH_REFRESH_DELAY_MS = 250L
        }
    }

private fun Context.applicationLabel(packageName: String): String =
    runCatching {
        packageManager
            .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
            .toString()
    }.getOrDefault(packageName)
