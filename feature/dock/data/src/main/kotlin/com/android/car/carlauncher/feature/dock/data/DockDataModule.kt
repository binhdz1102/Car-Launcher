package com.android.car.carlauncher.feature.dock.data

import com.android.car.carlauncher.feature.dock.domain.DockRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DockDataModule {
    @Binds
    abstract fun bindDockRepository(impl: AndroidDockRepository): DockRepository

    @Binds
    abstract fun bindDockOrderStore(impl: AndroidDockOrderStore): DockOrderStore
}
