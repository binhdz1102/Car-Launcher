package com.android.car.carlauncher.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.platform.CoroutineDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the configured provider contract; view creation remains in the Activity adapter. */
@Singleton
class AndroidWidgetHostController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val dispatchers: CoroutineDispatchers,
    ) : WidgetHostController {
        private val mutableState =
            MutableStateFlow(
                WidgetHostState(
                    appWidgetId = null,
                    display = DisplayTarget(displayId = 0, isDefaultDisplay = true),
                    isBound = false,
                ),
            )

        override val state: StateFlow<WidgetHostState> = mutableState.asStateFlow()

        override suspend fun bind(display: DisplayTarget): Result<WidgetHostState> =
            withContext(dispatchers.io) {
                runCatching {
                    val manager = AppWidgetManager.getInstance(context)
                    val providers =
                        manager.installedProviders
                            .filter { provider -> provider.provider.flattenToString() in configuredProviders() }
                    val widgetIds =
                        providers.flatMap { provider ->
                            manager.getAppWidgetIds(provider.provider).asList()
                        }
                    WidgetHostState(
                        appWidgetId = widgetIds.firstOrNull(),
                        display = display,
                        isBound = widgetIds.isNotEmpty(),
                        providerCount = providers.size,
                    ).also { state -> mutableState.value = state }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to inspect widget providers")
                }
            }

        override suspend fun unbind(): Result<Unit> =
            runCatching {
                mutableState.value = WidgetHostLifecycleReducer.stopped(mutableState.value)
            }

        private fun configuredProviders(): Set<String> =
            context.resources
                .getStringArray(
                    context.resources.getIdentifier(
                        "config_initialAppWidgets",
                        "array",
                        context.packageName,
                    ),
                ).toSet()

        private companion object {
            const val TAG = "CarLauncher.WidgetHostController"
        }
    }
