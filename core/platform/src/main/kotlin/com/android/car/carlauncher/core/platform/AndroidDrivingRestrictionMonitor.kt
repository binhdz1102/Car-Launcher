package com.android.car.carlauncher.core.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import com.android.car.carlauncher.core.model.DrivingRestriction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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

/** Converts the AAOS UXR listener into a stateful Flow with a fail-safe unavailable state. */
@Singleton
class AndroidDrivingRestrictionMonitor
    @Inject
    constructor(
        private val carServiceConnection: CarServiceConnection,
    ) : DrivingRestrictionMonitor {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @OptIn(ExperimentalCoroutinesApi::class)
        override val restrictions: StateFlow<UxrState> =
            carServiceConnection.car
                .flatMapLatest(::restrictionChanges)
                .stateIn(scope, SharingStarted.Eagerly, UxrState.Unavailable)

        private fun restrictionChanges(car: Car?): Flow<UxrState> {
            val manager =
                car?.let {
                    runCatching { it.getCarManager(CarUxRestrictionsManager::class.java) }.getOrNull()
                } ?: return flowOf(UxrState.Unavailable)
            return callbackFlow {
                val listener =
                    CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                        trySend(restrictions.toUxrState())
                    }
                runCatching {
                    manager.registerListener(listener)
                    trySend(manager.currentCarUxRestrictions.toUxrState())
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Car UX restrictions are unavailable")
                    trySend(UxrState.Unavailable)
                }
                awaitClose { runCatching { manager.unregisterListener() } }
            }
        }

        private fun CarUxRestrictions?.toUxrState(): UxrState {
            if (this == null) return UxrState.Unavailable
            val flags = activeRestrictions
            val requiresOptimization = isRequiresDistractionOptimization
            val level =
                when {
                    !requiresOptimization -> DrivingRestriction.UNRESTRICTED
                    flags and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD != 0 ->
                        DrivingRestriction.NO_KEYBOARD
                    flags and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP != 0 -> DrivingRestriction.NO_SETUP
                    else -> DrivingRestriction.FULLY_RESTRICTED
                }
            return UxrState(
                level = level,
                requiresDistractionOptimization = requiresOptimization,
                noKeyboard = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD != 0,
                serviceAvailable = true,
            )
        }

        private companion object {
            const val TAG = "CarLauncher.Platform.Uxr"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class DrivingRestrictionPlatformModule {
    @Binds
    abstract fun bindDrivingRestrictionMonitor(impl: AndroidDrivingRestrictionMonitor): DrivingRestrictionMonitor
}
