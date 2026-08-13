package com.android.car.carlauncher.feature.dock.data

import com.android.car.carlauncher.core.model.LauncherComponent
import kotlinx.coroutines.flow.Flow

/** Handles dual-write to the stock dock protobuf and DataStore during compatibility migration. */
interface DockOrderStore {
    val order: Flow<List<LauncherComponent>>

    suspend fun writeOrder(order: List<LauncherComponent>)

    suspend fun writeStockAndCurrent(serializedOrder: ByteArray)
}
