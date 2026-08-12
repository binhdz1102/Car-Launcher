package com.android.car.carlauncher.feature.media.data

import android.content.Context
import android.content.Intent
import com.android.car.carlauncher.feature.media.domain.AssistiveCard
import com.android.car.carlauncher.feature.media.domain.AssistiveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Stock-compatible fallback for the reference FakeWeatherModel until an OEM provider is added. */
@Singleton
class StaticAssistiveRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AssistiveRepository {
        private val mutableCards =
            MutableStateFlow(
                listOf(
                    AssistiveCard(
                        id = WEATHER_CARD_ID,
                        priority = DEFAULT_PRIORITY,
                        title = "Weather",
                        body = "Partly cloudy",
                        footer = "Weather information",
                    ),
                ),
            )

        override val cards: StateFlow<List<AssistiveCard>> = mutableCards.asStateFlow()

        override fun launch(card: AssistiveCard) {
            val uri = card.launchIntentUri ?: return
            runCatching {
                context.startActivity(
                    Intent.parseUri(uri, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Timber.tag(TAG).w(it, "Unable to launch assistive card=%s", card.id) }
        }

        private companion object {
            const val TAG = "CarLauncher.AssistiveRepository"
            const val WEATHER_CARD_ID = "fallback-weather"
            const val DEFAULT_PRIORITY = 100
        }
    }
