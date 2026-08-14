package com.android.car.carlauncher.feature.media.data

import android.annotation.SuppressLint
import android.car.Car
import android.car.CarProjectionManager
import android.car.projection.ProjectionStatus
import android.content.Context
import android.content.Intent
import com.android.car.carlauncher.core.platform.ApplicationScope
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.feature.media.domain.ProjectionCard
import com.android.car.carlauncher.feature.media.domain.ProjectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Flow adapter for CarProjectionManager that mirrors the stock ProjectionModel. */
@Singleton
@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidProjectionRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
        @param:ApplicationScope private val repositoryScope: CoroutineScope,
    ) : ProjectionRepository {
        private val mutableProjection = MutableStateFlow<ProjectionCard?>(null)

        override val projection: StateFlow<ProjectionCard?> = mutableProjection.asStateFlow()

        init {
            repositoryScope.launch {
                carConnection.car
                    .flatMapLatest(::projectionStatusEvents)
                    .collectLatest { event ->
                        mutableProjection.value = event?.toProjectionCard(context)
                    }
            }
        }

        override fun launchCurrentProjection() {
            val uri = projection.value?.launchIntentUri ?: return
            runCatching {
                context.startActivity(
                    Intent.parseUri(uri, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Timber.tag(TAG).w(it, "Unable to open projection application") }
        }

        private fun projectionStatusEvents(car: Car?): Flow<ProjectionEvent?> =
            callbackFlow {
                val manager =
                    car?.let { connectedCar ->
                        runCatching {
                            connectedCar.getCarManager(CarProjectionManager::class.java)
                        }.onFailure { Timber.tag(TAG).w(it, "Projection manager unavailable") }
                            .getOrNull()
                    }
                if (manager == null) {
                    trySend(null)
                    awaitClose { }
                    return@callbackFlow
                }
                val listener =
                    CarProjectionManager.ProjectionStatusListener { state, packageName, details ->
                        trySend(
                            ProjectionEvent(
                                state = state,
                                packageName = packageName,
                                details = details.orEmpty(),
                            ),
                        )
                    }
                val registered =
                    runCatching { manager.registerProjectionStatusListener(listener) }
                        .onFailure { Timber.tag(TAG).w(it, "Projection status listener unavailable") }
                        .isSuccess
                if (!registered) trySend(null)
                awaitClose {
                    if (registered) {
                        runCatching { manager.unregisterProjectionStatusListener(listener) }
                    }
                }
            }

        private companion object {
            const val TAG = "CarLauncher.ProjectionRepository"
        }
    }

private data class ProjectionEvent(
    val state: Int,
    val packageName: String?,
    val details: List<ProjectionStatus>,
) {
    fun toProjectionCard(context: Context): ProjectionCard? =
        packageName
            ?.takeUnless { state == ProjectionStatus.PROJECTION_STATE_INACTIVE }
            ?.let { targetPackage ->
                runCatching { context.packageManager.getApplicationInfo(targetPackage, 0) }
                    .getOrNull()
                    ?.let { appInfo ->
                        ProjectionCard(
                            packageName = targetPackage,
                            appLabel = context.packageManager.getApplicationLabel(appInfo).toString(),
                            statusMessage =
                                details
                                    .firstOrNull { it.packageName == targetPackage }
                                    ?.deviceStatusMessage(),
                            launchIntentUri =
                                context.packageManager
                                    .getLaunchIntentForPackage(targetPackage)
                                    ?.toUri(Intent.URI_INTENT_SCHEME),
                        )
                    }
            }
}

private fun ProjectionStatus.deviceStatusMessage(): String? {
    val devices = connectedMobileDevices
    val projecting = devices.filter(ProjectionStatus.MobileDevice::isProjecting)
    val nonProjecting = devices.filterNot(ProjectionStatus.MobileDevice::isProjecting)
    return when {
        projecting.size == 1 -> projecting.first().name
        projecting.isEmpty() && nonProjecting.size == 1 -> nonProjecting.first().name
        devices.isNotEmpty() -> "${devices.size} devices"
        else -> null
    }
}
