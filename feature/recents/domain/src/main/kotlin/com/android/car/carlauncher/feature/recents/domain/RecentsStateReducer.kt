package com.android.car.carlauncher.feature.recents.domain

/** Framework-free rules used when turning platform task records into Recents UI state. */
object RecentsStateReducer {
    fun stableTasks(tasks: List<RecentTask>): List<RecentTask> = tasks.distinctBy(RecentTask::taskId)

    fun canDismissToTask(
        taskComponent: String?,
        recentsComponent: String,
    ): Boolean = taskComponent != null && taskComponent != recentsComponent
}
