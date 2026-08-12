package com.android.car.carlauncher.feature.recents.data

/** Hidden QuickStep APIs are contained here and exposed to the domain as a suspend API. */
interface RecentTaskDataSource {
    suspend fun refresh()
}
