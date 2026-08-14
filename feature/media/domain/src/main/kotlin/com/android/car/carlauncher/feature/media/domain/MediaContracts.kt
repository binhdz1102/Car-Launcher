package com.android.car.carlauncher.feature.media.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Encoded artwork carries a stable content key. Views use that key to retain a decoded bitmap
 * across progress ticks instead of decoding the same album art once per second.
 */
data class MediaArtwork(
    val key: String,
    val encodedBytes: ByteArray,
)

data class MediaPlayback(
    val sourcePackage: String? = null,
    val sourceComponent: String? = null,
    val sourceLabel: String = "",
    val title: String = "",
    val artist: String = "",
    val artwork: MediaArtwork? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSeek: Boolean = false,
    val customActions: List<MediaCustomAction> = emptyList(),
) {
    /** Transitional accessor for legacy consumers while they move to [artwork]. */
    @Deprecated("Use artwork and its stable key instead.")
    val artworkBytes: ByteArray?
        get() = artwork?.encodedBytes
}

/** A transport action supplied by the active media session (for example thumbs-up). */
data class MediaCustomAction(
    val action: String,
    val title: String,
)

data class MediaSource(
    val componentName: String,
    val label: String,
)

data class MediaQueueItem(
    val id: Long,
    val title: String,
    val subtitle: String,
)

/** A source/title pair retained for the fullscreen media history surface. */
data class MediaHistoryItem(
    val id: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String,
    val subtitle: String = "",
    val artwork: MediaArtwork? = null,
)

/** Platform media-session boundary for the Home media card. */
interface MediaRepository {
    val playback: StateFlow<MediaPlayback>
    val sources: StateFlow<List<MediaSource>>
    val queue: StateFlow<List<MediaQueueItem>>
    val history: StateFlow<List<MediaHistoryItem>>

    fun playPause()

    fun previous()

    fun next()

    fun seekTo(positionMs: Long)

    fun selectSource(source: MediaSource)

    fun openMediaCenter()

    fun sendCustomAction(action: MediaCustomAction)
}

enum class CallCardState {
    RINGING,
    DIALING,
    ACTIVE,
    HOLDING,
    DISCONNECTING,
}

/** Immutable, UI-safe projection of the active telecom call. */
data class CallCard(
    val id: String,
    val packageName: String? = null,
    val appLabel: String = "Phone",
    val caller: String = "",
    val state: CallCardState,
    val isMuted: Boolean = false,
    val connectedElapsedRealtimeMs: Long? = null,
    val contactName: String? = null,
    val avatarBytes: ByteArray? = null,
)

interface CallRepository {
    val activeCall: StateFlow<CallCard?>

    fun toggleMute()

    fun endCall()

    fun openDialpad()
}

/** A projected-phone card mirrors the stock ProjectionModel without leaking Android types. */
data class ProjectionCard(
    val packageName: String,
    val appLabel: String,
    val statusMessage: String? = null,
    val launchIntentUri: String? = null,
    val deviceCount: Int = 0,
)

interface ProjectionRepository {
    val projection: StateFlow<ProjectionCard?>

    fun launchCurrentProjection()
}

/** Extensible fallback card used when no active projection supplies assistive content. */
data class AssistiveCard(
    val id: String,
    val priority: Int,
    val title: String,
    val body: String,
    val footer: String? = null,
    val launchIntentUri: String? = null,
    val deviceCount: Int = 0,
)

interface AssistiveRepository {
    val cards: StateFlow<List<AssistiveCard>>

    fun launch(card: AssistiveCard)
}

data class HomeCardsState(
    val media: MediaPlayback = MediaPlayback(),
    val mediaVariant: MediaCardVariant = MediaCardVariant.EMPTY,
    val activeCall: CallCard? = null,
    val projection: ProjectionCard? = null,
    val assistive: AssistiveCard? = null,
)

/**
 * Deterministic replacement for the stock presenter priority policy: calls take over the audio
 * card and an active projection takes precedence over configured assistive fallbacks.
 */
class HomeCardCoordinator(
    private val mediaRepository: MediaRepository,
    private val callRepository: CallRepository,
    private val projectionRepository: ProjectionRepository,
    private val assistiveRepository: AssistiveRepository,
    private val fullscreenMediaEnabled: Boolean = true,
) {
    val states: Flow<HomeCardsState> =
        kotlinx.coroutines.flow.combine(
            mediaRepository.playback,
            callRepository.activeCall,
            projectionRepository.projection,
            assistiveRepository.cards,
        ) { media, call, projection, assistiveCards ->
            HomeCardsState(
                media = media,
                mediaVariant =
                    MediaCardPolicy.visibleVariant(
                        hasActivePlayback = media.sourcePackage != null,
                        fullscreenEnabled = fullscreenMediaEnabled,
                    ),
                activeCall = call,
                projection = projection,
                assistive = MediaCardPolicy.assistiveCard(projection, assistiveCards),
            )
        }
}
