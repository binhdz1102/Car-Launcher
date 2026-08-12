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
) {
    /** Transitional accessor for legacy consumers while they move to [artwork]. */
    @Deprecated("Use artwork and its stable key instead.")
    val artworkBytes: ByteArray?
        get() = artwork?.encodedBytes
}

data class MediaSource(
    val componentName: String,
    val label: String,
)

data class MediaQueueItem(
    val id: Long,
    val title: String,
    val subtitle: String,
)

/** Platform media-session boundary for the Home media card. */
interface MediaRepository {
    val playback: StateFlow<MediaPlayback>
    val sources: StateFlow<List<MediaSource>>
    val queue: StateFlow<List<MediaQueueItem>>

    fun playPause()

    fun previous()

    fun next()

    fun seekTo(positionMs: Long)

    fun selectSource(source: MediaSource)

    fun openMediaCenter()
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
)

interface AssistiveRepository {
    val cards: StateFlow<List<AssistiveCard>>

    fun launch(card: AssistiveCard)
}

data class HomeCardsState(
    val media: MediaPlayback = MediaPlayback(),
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
                activeCall = call,
                projection = projection,
                assistive =
                    projection?.toAssistiveCard()
                        ?: assistiveCards.sortedBy(AssistiveCard::priority).firstOrNull(),
            )
        }

    private fun ProjectionCard.toAssistiveCard(): AssistiveCard =
        AssistiveCard(
            id = "projection:$packageName",
            priority = Int.MIN_VALUE,
            title = appLabel,
            body = "Projected phone",
            footer = statusMessage,
            launchIntentUri = launchIntentUri,
        )
}
