package com.android.car.carlauncher.feature.appgrid.data

import com.android.car.carlauncher.core.model.LauncherComponent

/** Compatibility seam for the stock `files/order.data` reader and the new DataStore writer. */
interface AppGridOrderStore {
    suspend fun read(): List<LauncherComponent>

    suspend fun write(order: List<LauncherComponent>)

    suspend fun clear()
}
