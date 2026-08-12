package com.android.car.carlauncher.feature.media.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.car.carlauncher.feature.media.domain.MediaArtwork

/** Small View-layer cache; the data key changes only when the encoded artwork changes. */
class MediaArtworkDecoder {
    private var key: String? = null
    private var bitmap: Bitmap? = null

    fun decode(artwork: MediaArtwork?): Bitmap? =
        when {
            artwork == null -> clear()
            artwork.key == key -> bitmap
            else -> decodeAndRemember(artwork)
        }

    private fun clear(): Bitmap? =
        null.also {
            key = null
            bitmap = null
        }

    private fun decodeAndRemember(artwork: MediaArtwork): Bitmap? =
        BitmapFactory
            .decodeByteArray(
                artwork.encodedBytes,
                0,
                artwork.encodedBytes.size,
            ).also { decoded ->
                key = artwork.key
                bitmap = decoded
            }
}

data class MediaUiState(
    val hasActiveSource: Boolean = false,
    val isRestricted: Boolean = false,
)
