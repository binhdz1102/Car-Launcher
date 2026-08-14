package com.android.car.carlauncher

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.view.Display
import androidx.appcompat.app.AppCompatActivity
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.appgrid.domain.AppGridMode
import com.android.car.carlauncher.feature.appgrid.presentation.AppGridFragment
import com.android.car.carlauncher.feature.launcher.presentation.LauncherFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/** HOME entry point. Feature state and TaskView boundaries live in launcher:presentation. */
@AndroidEntryPoint
@Suppress("TooManyFunctions", "ComplexCondition")
class CarLauncher : AppCompatActivity() {
    private val launcherFragmentTag = "launcher-root"
    private val passengerFragmentTag = "passenger-app-grid"
    private var launcherTaskId = ActivityTaskManager.INVALID_TASK_ID
    private var taskStackRegistered = false

    private val taskStackListener =
        object : TaskStackListener() {
            override fun onActivityRestartAttempt(
                task: ActivityManager.RunningTaskInfo,
                homeTaskVisible: Boolean,
                clearedTask: Boolean,
                wasVisible: Boolean,
            ) {
                val embeddedTaskId = fragment()?.embeddedTaskId()
                if (!homeTaskVisible && task.taskId == embeddedTaskId) {
                    bringLauncherToForeground()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherTaskId = taskId
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            setContentView(R.layout.car_launcher_multiwindow)
            return
        }
        setContentView(R.layout.activity_launcher_host)
        applySystemBarInsets()
        if (savedInstanceState == null) {
            attachContentForDisplay()
        }
        registerTaskStackListener()
        Timber.tag(TAG).i("CarLauncher created for uid=%d", Process.myUid())
        fragment()?.handleHostIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        registerTaskStackListener()
    }

    override fun onStop() {
        unregisterTaskStackListener()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            return
        }
        if (isPassengerDisplay()) {
            passengerFragment()?.updateMode(AppGridMode.ALL_APPS)
            return
        }
        fragment()?.handleHostIntent(intent)
        Timber.tag(TAG).d("Received new launcher intent: %s", intent.action)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        val wasMultiWindow = isInMultiWindowMode() || isInPictureInPictureMode()
        super.onConfigurationChanged(newConfig)
        val isMultiWindow = isInMultiWindowMode() || isInPictureInPictureMode()
        if (wasMultiWindow != isMultiWindow) {
            unregisterTaskStackListener()
            removeManagedFragments()
            if (isMultiWindow) {
                setContentView(R.layout.car_launcher_multiwindow)
            } else {
                setContentView(R.layout.activity_launcher_host)
                applySystemBarInsets()
                attachContentForDisplay()
                registerTaskStackListener()
            }
        }
    }

    private fun fragment(): LauncherFragment? {
        val fragment = supportFragmentManager.findFragmentByTag(launcherFragmentTag)
        return fragment as? LauncherFragment
    }

    private fun isPassengerDisplay(): Boolean {
        val isSecondaryDisplay = currentDisplayId() != Display.DEFAULT_DISPLAY
        return isSecondaryDisplay || supportsVisibleBackgroundUsers()
    }

    private fun currentDisplayId(): Int = display?.displayId ?: Display.DEFAULT_DISPLAY

    private fun supportsVisibleBackgroundUsers(): Boolean =
        runCatching {
            UserManager::class.java
                .getMethod("isVisibleBackgroundUsersOnDefaultDisplaySupported")
                .invoke(getSystemService(UserManager::class.java)) as Boolean
        }.getOrDefault(false)

    private fun attachContentForDisplay() {
        if (isPassengerDisplay()) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.launcher_container,
                    AppGridFragment.newInstance(AppGridMode.ALL_APPS),
                    passengerFragmentTag,
                ).commitNow()
            Timber.tag(TAG).i("Passenger display=%d hosts App Grid in HOME", currentDisplayId())
        } else {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.launcher_container, LauncherFragment(), launcherFragmentTag)
                .commitNow()
        }
    }

    private fun passengerFragment() = supportFragmentManager.findFragmentByTag(passengerFragmentTag) as? AppGridFragment

    private fun removeManagedFragments() {
        val transaction = supportFragmentManager.beginTransaction()
        listOf(launcherFragmentTag, passengerFragmentTag).forEach { tag ->
            supportFragmentManager.findFragmentByTag(tag)?.let(transaction::remove)
        }
        transaction.commitNowAllowingStateLoss()
    }

    private fun registerTaskStackListener() {
        if (taskStackRegistered || isInMultiWindowMode() || isInPictureInPictureMode() || isPassengerDisplay()) {
            return
        }
        runCatching {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener)
            taskStackRegistered = true
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Unable to register TaskStackListener")
        }
    }

    private fun unregisterTaskStackListener() {
        if (!taskStackRegistered) return
        runCatching { ActivityTaskManager.getService().unregisterTaskStackListener(taskStackListener) }
            .onFailure { throwable ->
                Timber.tag(TAG).w(throwable, "Unable to unregister TaskStackListener")
            }
        taskStackRegistered = false
    }

    private fun bringLauncherToForeground() {
        if (launcherTaskId == ActivityTaskManager.INVALID_TASK_ID) return
        getSystemService(ActivityManager::class.java)?.moveTaskToFront(launcherTaskId, 0)
    }

    private companion object {
        const val TAG = "CarLauncher"
    }
}
