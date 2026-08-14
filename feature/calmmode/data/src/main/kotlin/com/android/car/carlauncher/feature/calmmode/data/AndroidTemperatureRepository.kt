package com.android.car.carlauncher.feature.calmmode.data

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import com.android.car.carlauncher.core.platform.ApplicationScope
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.feature.calmmode.domain.CalmTemperature
import com.android.car.carlauncher.feature.calmmode.domain.TemperatureRepository
import com.android.car.carlauncher.feature.calmmode.domain.TemperatureUnit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Converts AAOS HVAC/outside-temperature callbacks to a lifecycle-safe Flow. */
@Singleton
class AndroidTemperatureRepository
    @Inject
    constructor(
        private val carConnection: CarServiceConnection,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : TemperatureRepository {
        override val temperature: StateFlow<CalmTemperature?> =
            carConnection.car
                .flatMapLatest { car -> car?.let(::observe) ?: flowOf(null) }
                .stateIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = null,
                )

        private fun observe(car: Car): Flow<CalmTemperature?> =
            callbackFlow {
                val manager =
                    runCatching { car.getCarManager(CarPropertyManager::class.java) }
                        .getOrNull()
                if (manager == null) {
                    trySend(null)
                    close()
                    return@callbackFlow
                }
                val callback =
                    object : CarPropertyManager.CarPropertyEventCallback {
                        override fun onChangeEvent(value: CarPropertyValue<Any>) {
                            if (value.propertyId == VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE ||
                                value.propertyId == VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS
                            ) {
                                trySend(read(manager))
                            }
                        }

                        override fun onErrorEvent(
                            propertyId: Int,
                            areaId: Int,
                        ) {
                            trySend(null)
                        }
                    }
                val registration =
                    runCatching {
                        manager.registerCallback(
                            callback,
                            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                            CarPropertyManager.SENSOR_RATE_ONCHANGE,
                        )
                        manager.registerCallback(
                            callback,
                            VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                            CarPropertyManager.SENSOR_RATE_ONCHANGE,
                        )
                        trySend(read(manager))
                    }
                if (registration.isFailure) {
                    val error = registration.exceptionOrNull()
                    Timber.tag(TAG).w(error, "Unable to observe outside temperature")
                    trySend(null)
                    close(error)
                    return@callbackFlow
                }
                awaitClose {
                    runCatching {
                        manager.unregisterCallback(
                            callback,
                            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                        )
                        manager.unregisterCallback(
                            callback,
                            VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                        )
                    }
                }
            }

        @Suppress("UNCHECKED_CAST")
        private fun read(manager: CarPropertyManager): CalmTemperature? {
            val areaId =
                runCatching {
                    manager.getAreaId(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, VehicleAreaSeat.SEAT_UNKNOWN)
                }.getOrElse { VehicleAreaSeat.SEAT_UNKNOWN }
            val value =
                runCatching {
                    manager.getProperty<Float>(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, areaId)?.value
                }.getOrNull() ?: return null
            val unitAreaId =
                runCatching {
                    manager.getAreaId(
                        VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                        VehicleAreaSeat.SEAT_UNKNOWN,
                    )
                }.getOrElse { VehicleAreaSeat.SEAT_UNKNOWN }
            val unit =
                runCatching {
                    manager
                        .getProperty<Int>(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS, unitAreaId)
                        ?.value
                }.getOrNull()
            return if (unit == VehicleUnit.FAHRENHEIT) {
                CalmTemperature(
                    value * CELSIUS_TO_FAHRENHEIT_SCALE + FAHRENHEIT_OFFSET,
                    TemperatureUnit.FAHRENHEIT,
                )
            } else {
                CalmTemperature(value, TemperatureUnit.CELSIUS)
            }
        }

        private companion object {
            const val TAG = "CarLauncher.TemperatureRepository"
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val CELSIUS_TO_FAHRENHEIT_SCALE = 9f / 5f
            const val FAHRENHEIT_OFFSET = 32f
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object TemperatureDataModule {
    @Provides
    @Singleton
    fun provideTemperatureRepository(impl: AndroidTemperatureRepository): TemperatureRepository = impl
}
