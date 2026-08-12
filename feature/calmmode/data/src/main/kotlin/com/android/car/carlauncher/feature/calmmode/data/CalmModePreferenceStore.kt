package com.android.car.carlauncher.feature.calmmode.data

interface CalmModePreferenceStore {
    suspend fun readEnabled(): Boolean

    suspend fun writeEnabled(enabled: Boolean)
}
