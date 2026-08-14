package com.android.car.docklib.task

import android.app.ActivityManager
import android.content.ComponentName
import com.android.car.docklib.media.MediaUtils

/** Resolves a running task to the actual media component when the media template is on top. */
object TaskUtils {
    @JvmStatic
    fun getComponentName(taskInfo: ActivityManager.RunningTaskInfo): ComponentName? {
        val component = taskInfo.baseActivity ?: taskInfo.baseIntent.component ?: return null
        return if (MediaUtils.isMediaComponent(component)) {
            MediaUtils.getMediaComponentName(taskInfo)
        } else {
            component
        }
    }
}
