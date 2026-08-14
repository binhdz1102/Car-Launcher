package com.android.car.carlauncher.feature.media.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeCardCoordinatorTest {
    @Test
    fun activeProjectionTakesPriorityOverAssistiveFallback() =
        runBlocking {
            val projection =
                ProjectionCard(
                    packageName = "com.example.projected",
                    appLabel = "Projection",
                    statusMessage = "Pixel",
                )
            val state =
                HomeCardCoordinator(
                    mediaRepository = FakeMediaRepository(),
                    callRepository = FakeCallRepository(),
                    projectionRepository = FakeProjectionRepository(projection),
                    assistiveRepository =
                        FakeAssistiveRepository(
                            listOf(
                                AssistiveCard(
                                    id = "weather",
                                    priority = 100,
                                    title = "Weather",
                                    body = "Partly cloudy",
                                ),
                            ),
                        ),
                ).states.first()

            assertEquals("projection:com.example.projected", state.assistive?.id)
            assertEquals("Pixel", state.assistive?.footer)
        }

    @Test
    fun noProjectionUsesLowestPriorityAssistiveCard() =
        runBlocking {
            val call =
                CallCard(
                    id = "call-1",
                    caller = "Driver",
                    state = CallCardState.ACTIVE,
                )
            val state =
                HomeCardCoordinator(
                    mediaRepository = FakeMediaRepository(),
                    callRepository = FakeCallRepository(call),
                    projectionRepository = FakeProjectionRepository(),
                    assistiveRepository =
                        FakeAssistiveRepository(
                            listOf(
                                AssistiveCard("weather", 100, "Weather", "Partly cloudy"),
                                AssistiveCard("notice", 10, "Notice", "Vehicle update"),
                            ),
                        ),
                ).states.first()

            assertEquals(call, state.activeCall)
            assertEquals("notice", state.assistive?.id)
        }

    @Test
    fun emptySourcesExposeNoCards() =
        runBlocking {
            val state =
                HomeCardCoordinator(
                    mediaRepository = FakeMediaRepository(),
                    callRepository = FakeCallRepository(),
                    projectionRepository = FakeProjectionRepository(),
                    assistiveRepository = FakeAssistiveRepository(emptyList()),
                ).states.first()

            assertNull(state.activeCall)
            assertNull(state.projection)
            assertNull(state.assistive)
        }

    private class FakeMediaRepository : MediaRepository {
        override val playback: StateFlow<MediaPlayback> = MutableStateFlow(MediaPlayback())
        override val sources: StateFlow<List<MediaSource>> = MutableStateFlow(emptyList())
        override val queue: StateFlow<List<MediaQueueItem>> = MutableStateFlow(emptyList())
        override val history: StateFlow<List<MediaHistoryItem>> = MutableStateFlow(emptyList())

        override fun playPause() = Unit

        override fun previous() = Unit

        override fun next() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun selectSource(source: MediaSource) = Unit

        override fun openMediaCenter() = Unit

        override fun sendCustomAction(action: MediaCustomAction) = Unit
    }

    private class FakeCallRepository(
        card: CallCard? = null,
    ) : CallRepository {
        override val activeCall: StateFlow<CallCard?> = MutableStateFlow(card)

        override fun toggleMute() = Unit

        override fun endCall() = Unit

        override fun openDialpad() = Unit
    }

    private class FakeProjectionRepository(
        card: ProjectionCard? = null,
    ) : ProjectionRepository {
        override val projection: StateFlow<ProjectionCard?> = MutableStateFlow(card)

        override fun launchCurrentProjection() = Unit
    }

    private class FakeAssistiveRepository(
        cards: List<AssistiveCard>,
    ) : AssistiveRepository {
        override val cards: StateFlow<List<AssistiveCard>> = MutableStateFlow(cards)

        override fun launch(card: AssistiveCard) = Unit
    }
}
