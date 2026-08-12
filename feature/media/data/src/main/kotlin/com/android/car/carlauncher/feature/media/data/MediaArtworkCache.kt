package com.android.car.carlauncher.feature.media.data

/**
 * Cache key policy used by the media data source. Bitmap decoding remains in the data layer so
 * presentation only receives a stable artwork key.
 */
class MediaArtworkCache {
    private val keys = LinkedHashSet<String>()

    fun remember(key: String) {
        keys += key
    }

    fun contains(key: String): Boolean = key in keys
}
