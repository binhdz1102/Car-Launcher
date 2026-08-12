package com.android.car.carlauncher.core.platform

import android.car.Car
import android.content.Context
import android.os.Handler
import android.os.Looper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide AAOS car-service boundary. Feature modules observe state rather than holding a
 * manager instance, so a car-service restart is represented as normal Flow state.
 */
interface CarServiceConnection {
    val car: StateFlow<Car?>
    val state: StateFlow<PlatformConnectionState>
}

@Singleton
class AndroidCarServiceConnection
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : CarServiceConnection {
        private val mutableCar = MutableStateFlow<Car?>(null)
        private val mutableState = MutableStateFlow<PlatformConnectionState>(PlatformConnectionState.Connecting)

        override val car: StateFlow<Car?> = mutableCar.asStateFlow()
        override val state: StateFlow<PlatformConnectionState> = mutableState.asStateFlow()

        // Car.createCar requires a Handler overload on this API level. The handler is contained
        // entirely in this Android callback adapter; no feature uses it for state or scheduling.
        @Suppress("UnusedPrivateProperty")
        private val client: Car? =
            runCatching {
                Car.createCar(
                    context.applicationContext,
                    Handler(Looper.getMainLooper()),
                    Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                ) { connectedCar, ready ->
                    mutableCar.value = connectedCar.takeIf { ready }
                    mutableState.value =
                        if (ready) PlatformConnectionState.Connected else PlatformConnectionState.Disconnected
                    Timber.tag(TAG).i("Car service ready=%s", ready)
                }
            }.onFailure { throwable ->
                mutableState.value = PlatformConnectionState.Failed(throwable)
                Timber.tag(TAG).e(throwable, "Unable to create Car service connection")
            }.getOrNull()

        private companion object {
            const val TAG = "CarLauncher.Platform.CarConnection"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class CarServicePlatformModule {
    @Binds
    abstract fun bindCarServiceConnection(impl: AndroidCarServiceConnection): CarServiceConnection
}
