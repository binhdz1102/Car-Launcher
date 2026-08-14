package com.android.car.carlauncher.feature.appgrid.data

import com.android.car.carlauncher.core.model.LauncherComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppGridOrderProtoTest {
    @Test
    fun writeAndReadPreservesOrderAndUser() {
        val file = temporaryOrderFile()
        try {
            val input =
                listOf(
                    LauncherComponent("com.example.maps", ".MapsActivity", userId = 10),
                    LauncherComponent("com.example.media", ".MediaActivity", userId = 10),
                )

            AppGridOrderProto.write(file, input)

            assertEquals(input, AppGridOrderProto.read(file, userId = 10))
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatedFileFallsBackWithoutDeletingSource() {
        val file = temporaryOrderFile()
        try {
            AppGridOrderProto.write(
                file,
                listOf(LauncherComponent("com.example.maps", ".MapsActivity", userId = 10)),
            )
            val original = file.readBytes()
            file.writeBytes(original.copyOf(original.size - 1))

            assertTrue(file.exists())
            assertEquals(emptyList<LauncherComponent>(), AppGridOrderProto.read(file, userId = 10))
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyFileIsAnEmptyOrder() {
        val file = temporaryOrderFile()
        try {
            file.writeBytes(ByteArray(0))

            assertEquals(emptyList<LauncherComponent>(), AppGridOrderProto.read(file, userId = 10))
        } finally {
            file.delete()
        }
    }

    private fun temporaryOrderFile(): File = File.createTempFile("car-launcher-order", ".data")
}
