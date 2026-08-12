package com.android.car.carlauncher.core.platform

import android.app.ActivityManager
import android.app.TaskInfo
import android.os.IBinder
import com.android.wm.shell.recents.IRecentTasks
import com.android.wm.shell.shared.GroupedTaskInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Process-local Flow boundary for the QuickStep binder handed to CarLauncher by CarSystemUI.
 * Its static methods intentionally support the framework Service callback, while repositories
 * consume [recentTasks] as StateFlow and never retain a stale binder.
 */
object QuickStepRecentTasksSession {
    private val mutableRecentTasks = MutableStateFlow<IRecentTasks?>(null)

    val recentTasks: StateFlow<IRecentTasks?> = mutableRecentTasks.asStateFlow()

    @JvmStatic
    fun initialize(binder: IBinder?) {
        mutableRecentTasks.value = binder?.let(IRecentTasks.Stub::asInterface)
        Timber.tag(TAG).i("QuickStep recent-tasks binder ready=%s", mutableRecentTasks.value != null)
    }

    @JvmStatic
    fun terminate() {
        mutableRecentTasks.value = null
    }

    fun read(
        maxTasks: Int,
        userId: Int,
    ): List<TaskInfo>? {
        val proxy = mutableRecentTasks.value ?: return null
        return runCatching {
            proxy
                .getRecentTasks(maxTasks, ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId)
                .orEmpty()
                .asSequence()
                .filter { it.isBaseType(GroupedTaskInfo.TYPE_FULLSCREEN) }
                .mapNotNull(GroupedTaskInfo::getTaskInfo1)
                .toList()
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "QuickStep recent-tasks binder call failed")
        }.getOrNull()
    }

    private const val TAG = "CarLauncher.Platform.QuickStep"
}
