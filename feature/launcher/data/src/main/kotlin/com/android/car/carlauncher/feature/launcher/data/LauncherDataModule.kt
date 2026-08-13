package com.android.car.carlauncher.feature.launcher.data

import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherDataModule {
    @Binds
    abstract fun bindLauncherAppsRepository(impl: LauncherAppsRepositoryImpl): LauncherAppsRepository
}
