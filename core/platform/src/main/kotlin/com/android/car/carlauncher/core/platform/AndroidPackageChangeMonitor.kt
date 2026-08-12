package com.android.car.carlauncher.core.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Converts LauncherApps callbacks into a shared, lifecycle-safe Flow. */
@Singleton
class AndroidPackageChangeMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : PackageChangeMonitor {
        private val launcherApps = context.getSystemService(LauncherApps::class.java)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        override val packageChanges: Flow<PackageChange> =
            callbackFlow {
                val callback =
                    object : LauncherApps.Callback() {
                        override fun onPackageAdded(
                            packageName: String,
                            user: UserHandle,
                        ) = emit(packageName, user, PackageChange.Type.ADDED)

                        override fun onPackageChanged(
                            packageName: String,
                            user: UserHandle,
                        ) = emit(packageName, user, PackageChange.Type.CHANGED)

                        override fun onPackageRemoved(
                            packageName: String,
                            user: UserHandle,
                        ) = emit(packageName, user, PackageChange.Type.REMOVED)

                        override fun onPackagesAvailable(
                            packageNames: Array<String>,
                            user: UserHandle,
                            replacing: Boolean,
                        ) = packageNames.forEach { packageName ->
                            emit(packageName, user, PackageChange.Type.CHANGED)
                        }

                        override fun onPackagesUnavailable(
                            packageNames: Array<String>,
                            user: UserHandle,
                            replacing: Boolean,
                        ) = packageNames.forEach { packageName ->
                            emit(packageName, user, PackageChange.Type.CHANGED)
                        }

                        private fun emit(
                            packageName: String,
                            user: UserHandle,
                            type: PackageChange.Type,
                        ) {
                            val result = trySend(PackageChange(packageName, user.platformIdentifier(), type))
                            result.exceptionOrNull()?.let { throwable ->
                                Timber.tag(TAG).w(throwable, "Package change could not be emitted")
                            }
                        }
                    }
                // Android exposes only a Handler registration overload for LauncherApps here.
                launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
                awaitClose { runCatching { launcherApps.unregisterCallback(callback) } }
            }.conflate()
                .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 0)

        private companion object {
            const val TAG = "CarLauncher.Platform.PackageChanges"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class PackageChangePlatformModule {
    @Binds
    abstract fun bindPackageChangeMonitor(impl: AndroidPackageChangeMonitor): PackageChangeMonitor
}

private fun UserHandle.platformIdentifier(): Int =
    runCatching {
        UserHandle::class.java
            .getMethod("getIdentifier")
            .invoke(this) as Int
    }.getOrDefault(hashCode())
