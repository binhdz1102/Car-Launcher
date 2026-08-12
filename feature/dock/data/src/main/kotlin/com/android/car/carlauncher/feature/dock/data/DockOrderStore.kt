package com.android.car.carlauncher.feature.dock.data

/** Handles dual-write to the stock dock protobuf and DataStore during compatibility migration. */
interface DockOrderStore {
    suspend fun writeStockAndCurrent(serializedOrder: ByteArray)
}
