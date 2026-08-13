package com.android.car.carlauncher.feature.dock.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.feature.dock.domain.DockItem
import com.android.car.carlauncher.feature.dock.domain.DockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDockRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val orderStore: DockOrderStore,
    ) : DockRepository {
        private val mediaServices = lazy { discoverMediaServices() }

        override val items: Flow<List<DockItem>> =
            orderStore.order
                .map { components ->
                    components.mapIndexed { index, component ->
                        DockItem(
                            component = component.copy(userId = currentUserId()),
                            position = index,
                            isMediaApp = component.flattened in mediaServices.value,
                        )
                    }
                }

        override suspend fun pin(
            component: LauncherComponent,
            position: Int,
        ): Result<Unit> =
            runCatching {
                require(position >= 0) { "Dock position must be non-negative" }
                val current = orderStore.order.first().toMutableList()
                current.removeAll { it.flattened == component.flattened }
                current.add(
                    position.coerceAtMost(current.size),
                    component.copy(userId = currentUserId()),
                )
                orderStore.writeOrder(current)
            }

        override suspend fun unpin(component: LauncherComponent): Result<Unit> =
            runCatching {
                orderStore.writeOrder(
                    orderStore.order.first().filterNot { it.flattened == component.flattened },
                )
            }

        private fun currentUserId(): Int =
            runCatching {
                UserHandle::class.java
                    .getMethod("getUserId", Int::class.javaPrimitiveType)
                    .invoke(null, Process.myUid()) as Int
            }.getOrDefault(0)

        private fun discoverMediaServices(): Set<String> =
            context.packageManager
                .queryIntentServices(
                    Intent("android.media.browse.MediaBrowserService"),
                    0,
                ).mapNotNull { info ->
                    info.serviceInfo?.let { service ->
                        ComponentName(service.packageName, service.name).flattenToString()
                    }
                }.toSet()
    }
