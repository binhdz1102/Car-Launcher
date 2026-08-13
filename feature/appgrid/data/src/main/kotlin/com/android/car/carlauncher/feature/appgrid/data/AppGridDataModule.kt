package com.android.car.carlauncher.feature.appgrid.data

import com.android.car.carlauncher.feature.appgrid.domain.AppGridRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppGridDataModule {
    @Binds
    abstract fun bindAppGridRepository(impl: AndroidAppGridRepository): AppGridRepository

    @Binds
    abstract fun bindOrderStore(impl: DualAppGridOrderStore): AppGridOrderStore

    @Binds
    abstract fun bindMirroringSessionObserver(impl: AndroidMirroringSessionObserver): MirroringSessionObserver
}
