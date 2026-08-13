package com.android.car.carlauncher.feature.widgets

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetDataModule {
    @Binds
    abstract fun bindWidgetHostController(impl: AndroidWidgetHostController): WidgetHostController
}
