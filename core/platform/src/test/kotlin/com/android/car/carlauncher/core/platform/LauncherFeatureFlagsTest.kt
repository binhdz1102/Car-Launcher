package com.android.car.carlauncher.core.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFeatureFlagsTest {
    @Test
    fun avdDefaultsMatchApi37Configuration() {
        val flags = LauncherFeatureFlags()

        assertFalse(flags.calmMode)
        assertTrue(flags.mediaSessionCard)
        assertTrue(flags.mediaCardFullscreen)
        assertTrue(flags.tosRestrictionsEnabled)
        assertFalse(flags.dockFeature)
    }

    @Test
    fun flagsAreInjectableForFeatureTests() {
        val flags =
            LauncherFeatureFlags(
                calmMode = true,
                mediaSessionCard = false,
                mediaCardFullscreen = false,
                tosRestrictionsEnabled = false,
                dockFeature = true,
            )

        assertTrue(flags.calmMode)
        assertFalse(flags.mediaSessionCard)
        assertFalse(flags.mediaCardFullscreen)
        assertFalse(flags.tosRestrictionsEnabled)
        assertTrue(flags.dockFeature)
    }
}
