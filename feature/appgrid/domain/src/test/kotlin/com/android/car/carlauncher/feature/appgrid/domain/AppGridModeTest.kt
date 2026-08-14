package com.android.car.carlauncher.feature.appgrid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppGridModeTest {
    @Test
    fun missingModeDefaultsToAllApps() {
        assertEquals(AppGridMode.ALL_APPS, AppGridMode.fromIntentValue(null))
    }

    @Test
    fun unknownModeFailsLikeAosp() {
        assertThrows(IllegalArgumentException::class.java) {
            AppGridMode.fromIntentValue("NOT_A_MODE")
        }
    }
}
