package com.android.car.carlauncher.feature.media.data

import android.graphics.Bitmap
import com.android.car.carlauncher.feature.media.domain.MediaArtwork
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts metadata artwork once per platform bitmap instance and gives presentation a stable
 * content key. The weak identity cache intentionally does not retain framework Bitmaps.
 */
@Singleton
class MediaArtworkCache
    @Inject
    constructor() {
        private val artworkByBitmap = WeakHashMap<Bitmap, MediaArtwork>()

        fun encode(bitmap: Bitmap?): MediaArtwork? =
            bitmap?.let { source ->
                synchronized(artworkByBitmap) {
                    artworkByBitmap[source]
                        ?: source
                            .toPngBytes()
                            ?.let { bytes ->
                                MediaArtwork(key = bytes.sha256(), encodedBytes = bytes)
                                    .also { artworkByBitmap[source] = it }
                            }
                }
            }
    }

private fun Bitmap.toPngBytes(): ByteArray? =
    runCatching {
        ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
            output.toByteArray()
        }
    }.getOrNull()

private fun ByteArray.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val PNG_QUALITY = 100
