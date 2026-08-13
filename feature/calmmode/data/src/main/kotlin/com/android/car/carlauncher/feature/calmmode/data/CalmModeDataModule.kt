package com.android.car.carlauncher.feature.calmmode.data

import com.android.car.carlauncher.feature.calmmode.domain.CalmModeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CalmModeDataModule {
    @Binds
    abstract fun bindCalmModeRepository(impl: AndroidCalmModeRepository): CalmModeRepository

    @Binds
    abstract fun bindCalmModePreferenceStore(impl: AndroidCalmModePreferenceStore): CalmModePreferenceStore
}
