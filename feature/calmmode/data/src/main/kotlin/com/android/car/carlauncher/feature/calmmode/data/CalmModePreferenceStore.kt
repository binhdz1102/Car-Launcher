package com.android.car.carlauncher.feature.calmmode.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calmModeDataStore by preferencesDataStore(name = "calm_mode")

interface CalmModePreferenceStore {
    val enabled: Flow<Boolean>

    suspend fun readEnabled(): Boolean

    suspend fun writeEnabled(enabled: Boolean)
}

@Singleton
class AndroidCalmModePreferenceStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CalmModePreferenceStore {
        override val enabled: Flow<Boolean> =
            context.calmModeDataStore.data.map { preferences -> preferences[ENABLED_KEY] ?: false }

        override suspend fun readEnabled(): Boolean = context.calmModeDataStore.data.first()[ENABLED_KEY] ?: false

        override suspend fun writeEnabled(enabled: Boolean) {
            context.calmModeDataStore.edit { preferences ->
                preferences[ENABLED_KEY] = enabled
            }
        }

        private companion object {
            val ENABLED_KEY = booleanPreferencesKey("enabled")
        }
    }
