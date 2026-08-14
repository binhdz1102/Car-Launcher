package com.android.car.carlauncher.feature.media.data

import com.android.car.carlauncher.core.platform.LauncherFeatureFlags
import com.android.car.carlauncher.feature.media.domain.AssistiveRepository
import com.android.car.carlauncher.feature.media.domain.CallRepository
import com.android.car.carlauncher.feature.media.domain.MediaRepository
import com.android.car.carlauncher.feature.media.domain.ProjectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaDataModule {
    @Binds
    abstract fun bindMediaRepository(impl: AndroidMediaRepository): MediaRepository

    @Binds
    abstract fun bindCallRepository(impl: AndroidCallRepository): CallRepository

    @Binds
    abstract fun bindProjectionRepository(impl: AndroidProjectionRepository): ProjectionRepository

    @Binds
    abstract fun bindAssistiveRepository(impl: StaticAssistiveRepository): AssistiveRepository

    companion object {
        @Provides
        @Singleton
        fun provideHomeCardCoordinator(
            mediaRepository: MediaRepository,
            callRepository: CallRepository,
            projectionRepository: ProjectionRepository,
            assistiveRepository: AssistiveRepository,
            featureFlags: LauncherFeatureFlags,
        ): com.android.car.carlauncher.feature.media.domain.HomeCardCoordinator =
            com.android.car.carlauncher.feature.media.domain.HomeCardCoordinator(
                mediaRepository = mediaRepository,
                callRepository = callRepository,
                projectionRepository = projectionRepository,
                assistiveRepository = assistiveRepository,
                fullscreenMediaEnabled = featureFlags.mediaCardFullscreen,
            )
    }
}
