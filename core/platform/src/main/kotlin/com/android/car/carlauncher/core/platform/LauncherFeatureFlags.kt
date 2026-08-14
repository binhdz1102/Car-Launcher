package com.android.car.carlauncher.core.platform

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Injectable mirror of the AOSP aconfig flags used by Car Launcher. Defaults match the API 37
 * AVD used by the parity harness; tests can replace the binding with a deterministic instance.
 */
data class LauncherFeatureFlags(
    val calmMode: Boolean = false,
    val mediaSessionCard: Boolean = true,
    val mediaCardFullscreen: Boolean = true,
    val tosRestrictionsEnabled: Boolean = true,
    val dockFeature: Boolean = false,
)

@Module
@InstallIn(SingletonComponent::class)
object LauncherFeatureFlagsModule {
    @Provides
    @Singleton
    fun provideLauncherFeatureFlags(): LauncherFeatureFlags = LauncherFeatureFlags()
}
