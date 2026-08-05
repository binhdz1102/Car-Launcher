package com.android.car.carlauncher.feature.launcher.data

import android.car.Car
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaQueueItem
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaRepository {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val car = runCatching { Car.createCar(context) }.getOrNull()
    private val carMediaManager = runCatching {
        car?.getCarManager(CarMediaManager::class.java)
    }.getOrNull()
    private val _playback = MutableStateFlow(MediaPlayback())
    private val _sources = MutableStateFlow(emptyList<MediaSource>())
    private val _queue = MutableStateFlow(emptyList<MediaQueueItem>())
    private var controller: MediaController? = null

    override val playback: StateFlow<MediaPlayback> = _playback.asStateFlow()
    override val sources: StateFlow<List<MediaSource>> = _sources.asStateFlow()
    override val queue: StateFlow<List<MediaQueueItem>> = _queue.asStateFlow()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshPlayback()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshPlayback()

        override fun onSessionDestroyed() {
            controller = null
            chooseActiveController()
        }
    }

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { chooseActiveController() }

    init {
        runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, null) }
            .onFailure { Timber.tag(TAG).w(it, "Media session listener unavailable") }
        runCatching {
            carMediaManager?.addMediaSourceListener(
                { refreshSources() },
                CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK,
            )
        }.onFailure { Timber.tag(TAG).w(it, "Car media source listener unavailable") }
        refreshSources()
        chooseActiveController()
    }

    override fun playPause() {
        val controls = controller?.transportControls ?: return
        if (_playback.value.isPlaying) controls.pause() else controls.play()
    }

    override fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    override fun next() {
        controller?.transportControls?.skipToNext()
    }

    override fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
    }

    override fun selectSource(source: MediaSource) {
        val component = ComponentName.unflattenFromString(source.componentName) ?: return
        runCatching {
            carMediaManager?.setMediaSource(component, CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
        }.onFailure { Timber.tag(TAG).w(it, "Unable to select media source") }
    }

    override fun openMediaCenter() {
        val packageName = _playback.value.sourcePackage ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        runCatching { ContextCompat.startActivity(context, intent, null) }
            .onFailure { Timber.tag(TAG).w(it, "Unable to open media center") }
    }

    private fun chooseActiveController() {
        val next = runCatching {
            sessionManager.getActiveSessions(null)
                .sortedByDescending { active ->
                    if (active.playbackState?.state == PlaybackState.STATE_PLAYING) 1 else 0
                }
                .firstOrNull()
        }.getOrNull()
        if (next?.sessionToken == controller?.sessionToken) {
            refreshPlayback()
            return
        }
        controller?.unregisterCallback(controllerCallback)
        controller = next
        next?.registerCallback(controllerCallback)
        refreshPlayback()
    }

    private fun refreshSources() {
        _sources.value = runCatching {
            carMediaManager?.getLastMediaSources(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                .orEmpty()
                .distinct()
                .map { component ->
                    val label = runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(component.packageName, 0),
                        ).toString()
                    }.getOrDefault(component.packageName)
                    MediaSource(component.flattenToString(), label)
                }
        }.getOrDefault(emptyList())
    }

    private fun refreshPlayback() {
        val active = controller
        if (active == null) {
            _playback.value = MediaPlayback()
            _queue.value = emptyList()
            return
        }
        val metadata = active.metadata
        val state = active.playbackState
        val actions = state?.actions ?: 0L
        _playback.value = MediaPlayback(
            sourcePackage = active.packageName,
            sourceLabel = applicationLabel(active.packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Nothing playing",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            artworkBytes = (
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
        _queue.value = active.queue.orEmpty().map { item ->
            MediaQueueItem(
                id = item.queueId,
                title = item.description.title?.toString().orEmpty(),
                subtitle = item.description.subtitle?.toString().orEmpty(),
            )
        }
    }

    private fun applicationLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrDefault(packageName)

    private fun Bitmap.toArtworkBytes(): ByteArray? = runCatching {
        ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }.getOrNull()

    private companion object {
        const val TAG = "CarLauncher.MediaRepository"
    }
}
