package com.android.car.carlauncher.feature.launcher.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LauncherAppsRepository {
    val launchableApps: Flow<List<LaunchableApp>>
    suspend fun navigationTarget(): Result<EmbeddedAppTarget>
    suspend fun saveOrderedComponents(componentNames: List<String>)
}

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
