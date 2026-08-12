package com.android.car.carlauncher.feature.media.domain

import kotlinx.coroutines.flow.Flow

data class MediaCard(
    val packageName: String,
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val artworkKey: String?,
)

interface MediaRepository {
    val card: Flow<MediaCard?>

    suspend fun togglePlayback(): Result<Unit>

    suspend fun skipToNext(): Result<Unit>
}
