package com.android.car.carlauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherModelTest {
    @Test
    fun componentFlattensToTheAndroidComponentShape() {
        val component =
            LauncherComponent(
                packageName = "com.example.maps",
                className = "com.example.maps.MapActivity",
                userId = 10,
            )

        assertEquals("com.example.maps/com.example.maps.MapActivity", component.flattened)
    }
}
