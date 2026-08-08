package com.android.car.carlauncher.feature.launcher.data

import android.app.ActivityManager
import android.app.TaskInfo
import android.os.IBinder
import com.android.wm.shell.recents.IRecentTasks
import com.android.wm.shell.shared.GroupedTaskInfo
import timber.log.Timber

/** Process-local QuickStep session initialized by SystemUI's onInitialize binder callback. */
object RecentTasksSession {
    @Volatile private var recentTasks: IRecentTasks? = null

    fun initialize(binder: IBinder?) {
        recentTasks = binder?.let(IRecentTasks.Stub::asInterface)
        Timber.tag(TAG).i("QuickStep recent-tasks binder ready=%s", recentTasks != null)
    }

    fun terminate() {
        recentTasks = null
    }

    fun read(
        maxTasks: Int,
        userId: Int,
    ): List<TaskInfo>? {
        val proxy = recentTasks ?: return null
        return runCatching {
            proxy
                .getRecentTasks(maxTasks, ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId)
                .orEmpty()
                .asSequence()
                .filter { it.isBaseType(GroupedTaskInfo.TYPE_FULLSCREEN) }
                .mapNotNull(GroupedTaskInfo::getTaskInfo1)
                .toList()
        }.onFailure {
            Timber.tag(TAG).w(it, "QuickStep recent-tasks binder call failed")
        }.getOrNull()
    }

    private const val TAG = "CarLauncher.RecentSession"
}
