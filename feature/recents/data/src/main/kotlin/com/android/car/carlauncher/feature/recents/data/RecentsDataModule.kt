package com.android.car.carlauncher.feature.recents.data

import com.android.car.carlauncher.feature.recents.domain.RecentsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecentsDataModule {
    @Binds
    abstract fun bindRecentsRepository(impl: AndroidRecentsRepository): RecentsRepository
}
