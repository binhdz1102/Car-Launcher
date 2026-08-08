package com.android.car.carlauncher.core.common

import android.car.Car
import android.content.Context
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single non-blocking Car service connection used by the launcher process.
 *
 * Car service is restarted independently from applications during development. Consumers observe
 * [car] instead of caching a manager forever, so they automatically reconnect after service death.
 */
@Singleton
class CarServiceConnection
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val mutableCar = MutableStateFlow<Car?>(null)

        val car: StateFlow<Car?> = mutableCar.asStateFlow()

        @Suppress("UnusedPrivateProperty")
        private val client =
            Car.createCar(
                context.applicationContext,
                Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
            ) { connectedCar, ready ->
                mutableCar.value = connectedCar.takeIf { ready }
                Timber.tag(TAG).i("Car service ready=%s", ready)
            }

        private companion object {
            const val TAG = "CarLauncher.CarConnection"
        }
    }
