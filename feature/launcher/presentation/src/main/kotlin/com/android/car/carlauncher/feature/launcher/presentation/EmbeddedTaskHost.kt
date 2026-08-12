package com.android.car.carlauncher.feature.launcher.presentation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.car.app.CarActivityManager
import android.car.app.CarTaskViewController
import android.car.app.CarTaskViewControllerCallback
import android.car.app.CarTaskViewControllerHostLifecycle
import android.car.app.ControlledRemoteCarTaskView
import android.car.app.ControlledRemoteCarTaskViewCallback
import android.car.app.ControlledRemoteCarTaskViewConfig
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedAppTarget
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executor

/** Android boundary for AAOS ControlledRemoteCarTaskView. Domain code never sees TaskView types. */
interface EmbeddedTaskHost {
    val view: FrameLayout

    fun load(target: EmbeddedAppTarget)

    fun setVisible(visible: Boolean)

    fun onHostNewIntent()

    fun restoreAfterHostInteraction()

    fun release()
}

// RemoteCarTaskView annotates client-side proxy calls with SystemUI's registration permission.
// The stock launcher does not request that permission; it uses MANAGE_CAR_SYSTEM_UI instead.
@SuppressLint("MissingPermission")
class AndroidEmbeddedTaskHost(
    private val activity: Activity,
    private val carConnection: CarServiceConnection,
    private val onTaskAppeared: (String) -> Unit,
    private val onError: (String, String) -> Unit,
) : EmbeddedTaskHost {
    override val view = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }

    private val windowContext: Context =
        activity.createWindowContext(
            WindowManager.LayoutParams.TYPE_APPLICATION_STARTING,
            null,
        )
    private val executor: Executor = windowContext.mainExecutor
    private val hostLifecycle = CarTaskViewControllerHostLifecycle()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = activity as? LifecycleOwner
    private var carActivityManager: CarActivityManager? = null
    private var controller: CarTaskViewController? = null
    private var taskView: ControlledRemoteCarTaskView? = null
    private var requestedTarget: EmbeddedAppTarget? = null
    private var controllerRequestInFlight = false
    private var awaitingReplacement = false
    private var released = false
    private var controllerTimeoutJob: Job? = null
    private var timeoutJob: Job? = null
    private var restoreJob: Job? = null
    private var hostInteractionGeneration = 0

    private val layoutListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            taskView?.updateWindowBounds()
        }

    private val activityLifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                hostLifecycle.hostDisappeared()
            }
        }

    override fun load(target: EmbeddedAppTarget) {
        if (!released) {
            val previous = requestedTarget
            requestedTarget = target
            if (controller == null) requestController()
            when {
                taskView == null -> createTaskViewIfPossible()
                previous?.componentName == target.componentName -> Unit
                else -> {
                    awaitingReplacement = true
                    runCatching {
                        taskView?.setTaskVisibility(true)
                        taskView?.replaceActivityIntent(target.toIntent())
                        armTimeout(target)
                        Timber.tag(TAG).d("Replacing embedded task with %s", target.componentName)
                    }.onFailure {
                        reportError(
                            "Cannot embed ${target.label}",
                            it.message ?: "The activity rejected embedding.",
                        )
                    }
                }
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        if (visible) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            taskView?.setTaskVisibility(true)
            taskView?.showEmbeddedTask()
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            taskView?.setTaskVisibility(false)
        }
    }

    override fun onHostNewIntent() {
        if (released) return
        hostLifecycle.hostAppeared()
        taskView?.showEmbeddedTask()
        Timber.tag(TAG).d("Host appeared after a new launcher intent")
    }

    override fun restoreAfterHostInteraction() {
        if (released || requestedTarget == null || taskView == null) return
        val generation = ++hostInteractionGeneration
        restoreJob?.cancel()
        restoreJob =
            scope.launch {
                delay(HOST_INTERACTION_RESTORE_DELAY_MS)
                if (released || hostInteractionGeneration != generation) return@launch
                hostLifecycle.hostAppeared()
                taskView?.setTaskVisibility(false)
                delay(FRAME_DELAY_MS)
                if (!released && hostInteractionGeneration == generation) {
                    taskView?.showEmbeddedTask()
                }
            }
    }

    override fun release() {
        if (released) return
        released = true
        timeoutJob?.cancel()
        controllerTimeoutJob?.cancel()
        restoreJob?.cancel()
        taskView?.let { remoteTaskView ->
            view.removeView(remoteTaskView)
            remoteTaskView.release()
        }
        controller?.release()
        lifecycleOwner?.lifecycle?.removeObserver(activityLifecycleObserver)
        view.removeOnLayoutChangeListener(layoutListener)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        hostLifecycle.hostDestroyed()
        carActivityManager = null
        controller = null
        taskView = null
        scope.cancel()
        Timber.tag(TAG).d("Released embedded task host")
    }

    private val controllerCallback =
        object : CarTaskViewControllerCallback {
            override fun onConnected(connectedController: CarTaskViewController) {
                if (released) {
                    connectedController.release()
                    return
                }
                controller = connectedController
                controllerRequestInFlight = false
                controllerTimeoutJob?.cancel()
                Timber.tag(TAG).i("CarTaskViewController connected")
                createTaskViewIfPossible()
            }

            override fun onDisconnected(disconnectedController: CarTaskViewController) {
                controller = null
                controllerRequestInFlight = false
                if (!released) {
                    taskView?.let { remoteTaskView ->
                        view.removeView(remoteTaskView)
                        remoteTaskView.release()
                    }
                    taskView = null
                    armControllerTimeout()
                    reportError("TaskView disconnected", "The car activity service disconnected.")
                }
            }
        }

    private fun requestController() {
        val manager = carActivityManager ?: return
        if (released || controller != null || controllerRequestInFlight) return
        controllerRequestInFlight = true
        runCatching {
            manager.getCarTaskViewController(
                windowContext,
                hostLifecycle,
                executor,
                controllerCallback,
            )
            armControllerTimeout()
            Timber.tag(TAG).i("Requested CarTaskViewController")
        }.onFailure {
            controllerRequestInFlight = false
            controllerTimeoutJob?.cancel()
            reportError("TaskView unavailable", it.message ?: "Unable to connect to car activity service.")
        }
    }

    private fun createTaskViewIfPossible() {
        val connectedController = controller
        val target = requestedTarget
        if (connectedController != null && target != null) {
            if (taskView == null && !released) {
                runCatching {
                    connectedController.createControlledRemoteCarTaskView(
                        ControlledRemoteCarTaskViewConfig
                            .Builder()
                            .setActivityIntent(target.toIntent())
                            .setShouldAutoRestartOnTaskRemoval(false)
                            .build(),
                        executor,
                        taskCallback,
                    )
                }.onFailure {
                    reportError(
                        "TaskView creation failed",
                        it.message ?: "Unable to create embedded surface.",
                    )
                }
            }
        }
    }

    private val taskCallback =
        object : ControlledRemoteCarTaskViewCallback {
            override fun onTaskViewCreated(created: ControlledRemoteCarTaskView) {
                if (released) {
                    created.release()
                    return
                }
                taskView = created
                view.addView(
                    created,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                view.doOnLayout { created.updateWindowBounds() }
                armTimeout(requestedTarget ?: return)
                Timber.tag(TAG).i("ControlledRemoteCarTaskView created")
            }

            override fun onTaskAppeared(taskInfo: ActivityManager.RunningTaskInfo) {
                timeoutJob?.cancel()
                awaitingReplacement = false
                taskView?.updateWindowBounds()
                val component =
                    taskInfo.baseIntent.component?.flattenToString()
                        ?: taskInfo.topActivity?.flattenToString()
                if (component == null) {
                    reportError("Unknown embedded activity", "The vehicle returned no embedded component.")
                } else {
                    onTaskAppeared(component)
                }
            }

            override fun onTaskViewInitialized() {
                Timber.tag(TAG).i("ControlledRemoteCarTaskView initialized")
            }

            override fun onTaskVanished(taskInfo: ActivityManager.RunningTaskInfo) {
                if (awaitingReplacement || released) return
                val target = requestedTarget ?: return
                if (target.type == EmbeddedTargetType.NAVIGATION) {
                    runCatching { taskView?.startActivity() }
                        .onFailure { reportError("Navigation closed", "Unable to restart navigation.") }
                } else {
                    reportError("Embedded app closed", "${target.label} left the embedded task.")
                }
            }

            override fun onTaskViewReleased() {
                taskView?.let(view::removeView)
                taskView = null
                Timber.tag(TAG).d("ControlledRemoteCarTaskView released by car service")
            }
        }

    // Keep initialization after both callback objects: Kotlin initializes properties in source
    // order, and CarSystemUI may invoke the controller callback immediately on a clean boot.
    init {
        view.addOnLayoutChangeListener(layoutListener)
        runCatching {
            activity.enableTrustedOverlay(PRIVATE_FLAG_TRUSTED_OVERLAY)
            lifecycleOwner?.lifecycle?.addObserver(activityLifecycleObserver)
            armControllerTimeout()
            scope.launch {
                carConnection.car.collectLatest { car ->
                    carActivityManager =
                        car?.let {
                            runCatching { it.getCarManager(CarActivityManager::class.java) }.getOrNull()
                        }
                    if (carActivityManager != null) requestController()
                }
            }
        }.onFailure {
            reportError("TaskView unavailable", it.message ?: "Unable to connect to car activity service.")
        }
    }

    private fun armTimeout(target: EmbeddedAppTarget) {
        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(TASK_APPEAR_TIMEOUT_MS)
                if (!released && requestedTarget?.componentName == target.componentName) {
                    reportError(
                        "Embedding timed out",
                        "${target.label} did not appear in the embedded pane.",
                    )
                }
            }
    }

    private fun armControllerTimeout() {
        controllerTimeoutJob?.cancel()
        controllerTimeoutJob =
            scope.launch {
                delay(CONTROLLER_CONNECT_TIMEOUT_MS)
                if (!released && controller == null) {
                    reportControllerUnavailable()
                }
            }
    }

    private fun reportControllerUnavailable() {
        reportError(
            "TaskView unavailable",
            "CarSystemUI did not register the car activity proxy on this device.",
        )
    }

    private fun reportError(
        title: String,
        message: String,
    ) {
        Timber.tag(TAG).w("%s: %s", title, message)
        onError(title, message)
    }

    private companion object {
        const val TAG = "CarLauncher.EmbeddedTaskHost"
        const val CONTROLLER_CONNECT_TIMEOUT_MS = 10_000L
        const val TASK_APPEAR_TIMEOUT_MS = 8_000L
        const val HOST_INTERACTION_RESTORE_DELAY_MS = 100L
        const val FRAME_DELAY_MS = 16L
        const val PRIVATE_FLAG_TRUSTED_OVERLAY = 0x20000000
    }
}

// Standalone Gradle builds cannot compile against this hidden Window method directly. The APK is
// platform-signed and updates a /system/priv-app, matching the exemption used by the stock launcher.
@SuppressLint("SoonBlockedPrivateApi")
private fun Activity.enableTrustedOverlay(privateFlag: Int) {
    runCatching {
        Window::class.java
            .getDeclaredMethod(
                "addPrivateFlags",
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            .invoke(window, privateFlag)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }.onFailure {
        Timber.tag("CarLauncher.EmbeddedTaskHost").w(
            it,
            "Trusted TaskView overlay flag is unavailable",
        )
    }
}

private fun EmbeddedAppTarget.toIntent(): Intent {
    val component =
        ComponentName.unflattenFromString(componentName)
            ?: error("Invalid target component: $componentName")
    return Intent(Intent.ACTION_MAIN)
        .addCategory(
            if (type == EmbeddedTargetType.NAVIGATION) {
                Intent.CATEGORY_APP_MAPS
            } else {
                Intent.CATEGORY_LAUNCHER
            },
        ).setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
}

private fun View.doOnLayout(action: () -> Unit) {
    if (isLaidOut) {
        action()
    } else {
        addOnLayoutChangeListener(
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    view: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    removeOnLayoutChangeListener(this)
                    action()
                }
            },
        )
    }
}
