package com.android.car.carlauncher.feature.calmmode.data

import android.content.Context
import com.android.car.carlauncher.core.platform.LauncherFeatureFlags
import com.android.car.carlauncher.feature.calmmode.domain.CalmModeRepository
import com.android.car.carlauncher.feature.calmmode.domain.CalmModeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCalmModeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val preferenceStore: CalmModePreferenceStore,
        private val featureFlags: LauncherFeatureFlags,
    ) : CalmModeRepository {
        override val state: Flow<CalmModeState> =
            preferenceStore.enabled.map { enabled ->
                CalmModeState(
                    enabled = featureFlags.calmMode && enabled,
                    title =
                        context.resources
                            .getIdentifier("calm_mode_title", "string", context.packageName)
                            .takeIf { it != 0 }
                            ?.let(context::getString)
                            ?: "Calm mode",
                )
            }

        override suspend fun setEnabled(enabled: Boolean): Result<Unit> =
            runCatching {
                if (featureFlags.calmMode) preferenceStore.writeEnabled(enabled)
            }
    }
