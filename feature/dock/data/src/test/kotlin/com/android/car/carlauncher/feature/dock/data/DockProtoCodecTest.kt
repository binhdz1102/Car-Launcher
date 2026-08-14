package com.android.car.carlauncher.feature.dock.data

import com.android.car.carlauncher.core.model.LauncherComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class DockProtoCodecTest {
    @Test
    fun roundTripPreservesPositionAndComponent() {
        val input =
            listOf(
                LauncherComponent("com.example.maps", ".MapsActivity", userId = 0),
                LauncherComponent("com.example.media", ".MediaActivity", userId = 0),
            )

        assertEquals(input, DockProtoCodec.decode(DockProtoCodec.encode(input)))
    }

    @Test
    fun emptyOrderEncodesToEmptyFile() {
        assertEquals(emptyList<LauncherComponent>(), DockProtoCodec.decode(DockProtoCodec.encode(emptyList())))
    }

    @Test
    fun truncatedMessageFallsBackToEmptyOrder() {
        val encoded = DockProtoCodec.encode(listOf(LauncherComponent("com.example.maps", ".Maps", 0)))

        assertEquals(emptyList<LauncherComponent>(), DockProtoCodec.decode(encoded.copyOf(encoded.size - 1)))
    }

    @Test
    fun corruptVarintFallsBackToEmptyOrder() {
        assertEquals(emptyList<LauncherComponent>(), DockProtoCodec.decode(byteArrayOf(0x80.toByte())))
    }
}
