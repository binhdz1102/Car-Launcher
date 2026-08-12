package com.android.car.carlauncher.core.common

import android.car.Car
import android.content.Context
import com.android.car.carlauncher.core.platform.AndroidCarServiceConnection
import com.android.car.carlauncher.core.platform.PlatformConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source-compatible bridge for clients compiled before the platform boundary was introduced.
 * New code must inject [com.android.car.carlauncher.core.platform.CarServiceConnection].
 */
@Deprecated(
    message = "Use core.platform.CarServiceConnection",
    replaceWith = ReplaceWith("CarServiceConnection", "com.android.car.carlauncher.core.platform.CarServiceConnection"),
)
@Singleton
class CarServiceConnection
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val delegate = AndroidCarServiceConnection(context)

        val car: StateFlow<Car?> = delegate.car
        val state: StateFlow<PlatformConnectionState> = delegate.state
    }
