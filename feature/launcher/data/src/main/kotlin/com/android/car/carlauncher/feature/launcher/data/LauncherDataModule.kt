package com.android.car.carlauncher.feature.launcher.data

import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.MediaRepository
import com.android.car.carlauncher.feature.launcher.domain.RecentTasksRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherDataModule {
    @Binds
    abstract fun bindLauncherAppsRepository(impl: LauncherAppsRepositoryImpl): LauncherAppsRepository

    @Binds
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    abstract fun bindRecentTasksRepository(impl: RecentTasksRepositoryImpl): RecentTasksRepository
}
