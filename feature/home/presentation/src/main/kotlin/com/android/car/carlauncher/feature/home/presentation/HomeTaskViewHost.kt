package com.android.car.carlauncher.feature.home.presentation

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
import android.os.Build
import android.os.Process
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.car.carlauncher.core.platform.ApplicationScope
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.core.platform.CoroutineDispatchers
import com.android.car.carlauncher.core.platform.PackageChange
import com.android.car.carlauncher.core.platform.PackageChangeMonitor
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTargetType
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executor

/** Normalized, lifecycle-safe events emitted by the controlled AAOS TaskView. */
sealed interface HomeTaskViewEvent {
    data class TaskAppeared(
        val taskId: Int,
        val componentName: String,
    ) : HomeTaskViewEvent

    data class TaskInfoChanged(
        val taskId: Int,
        val componentName: String,
    ) : HomeTaskViewEvent

    data class Failure(
        val title: String,
        val message: String,
    ) : HomeTaskViewEvent

    data object Recovering : HomeTaskViewEvent
}

/** UI boundary for a HOME-controlled TaskView. No Android task classes leave this boundary. */
interface HomeTaskViewHost {
    val view: FrameLayout
    val events: SharedFlow<HomeTaskViewEvent>
    val embeddedTaskId: Int?

    fun load(target: HomeEmbeddedTaskTarget)

    fun setVisible(visible: Boolean)

    fun onHostNewIntent()

    fun restoreAfterHostInteraction()

    fun release()
}

// RemoteCarTaskView annotates client-side proxy calls with SystemUI's registration permission.
// The stock launcher uses MANAGE_CAR_SYSTEM_UI instead, which this platform-signed APK declares.
@SuppressLint("MissingPermission")
@Suppress("TooManyFunctions") // The AAOS TaskView lifecycle is intentionally centralized here.
class AndroidHomeTaskViewHost(
    private val activity: Activity,
    private val carConnection: CarServiceConnection,
    private val packageChangeMonitor: PackageChangeMonitor,
    private val dispatchers: CoroutineDispatchers,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : HomeTaskViewHost {
    override val view = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    override var embeddedTaskId: Int? = null
        private set

    private val mutableEvents = MutableSharedFlow<HomeTaskViewEvent>(replay = 1, extraBufferCapacity = 16)
    override val events: SharedFlow<HomeTaskViewEvent> = mutableEvents.asSharedFlow()

    private val windowContext: Context =
        activity.createWindowContext(
            WindowManager.LayoutParams.TYPE_APPLICATION_STARTING,
            null,
        )
    private val executor: Executor = windowContext.mainExecutor
    private val hostLifecycle = CarTaskViewControllerHostLifecycle()

    // The application scope owns the process lifetime; this child is cancelled with the host
    // view and therefore cannot leak a TaskView callback after the fragment is destroyed.
    private val scope =
        CoroutineScope(
            applicationScope.coroutineContext +
                dispatchers.main +
                SupervisorJob(applicationScope.coroutineContext[Job]),
        )
    private val lifecycleOwner = activity as? LifecycleOwner
    private val currentUserId = Process.myUid() / PER_USER_RANGE
    private val taskViewRestartPackages: Set<String> by lazy {
        stringArrayResource("config_taskViewPackages").toSet()
    }
    private var carActivityManager: CarActivityManager? = null
    private var controller: CarTaskViewController? = null
    private var taskView: ControlledRemoteCarTaskView? = null
    private var requestedTarget: HomeEmbeddedTaskTarget? = null
    private var controllerRequestInFlight = false
    private var awaitingReplacement = false

    @Volatile
    private var released = false
    private var controllerRetryAttempt = 0
    private var controllerTimeoutJob: Job? = null
    private var controllerRetryJob: Job? = null

    @Volatile
    private var taskTimeoutJob: Job? = null
    private var taskRecreateJob: Job? = null
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

    override fun load(target: HomeEmbeddedTaskTarget) {
        if (released) return

        val previous = requestedTarget
        requestedTarget = target
        if (controller == null) requestController()
        when {
            taskView == null -> createTaskViewIfPossible()
            previous?.componentName == target.componentName -> Unit
            else -> replaceActivity(target)
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
        taskTimeoutJob?.cancel()
        controllerTimeoutJob?.cancel()
        controllerRetryJob?.cancel()
        taskRecreateJob?.cancel()
        restoreJob?.cancel()
        releaseTaskView()
        controller?.release()
        lifecycleOwner?.lifecycle?.removeObserver(activityLifecycleObserver)
        view.removeOnLayoutChangeListener(layoutListener)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        hostLifecycle.hostDestroyed()
        carActivityManager = null
        controller = null
        scope.cancel()
        Timber.tag(TAG).d("Released HOME task host")
    }

    private val controllerCallback =
        object : CarTaskViewControllerCallback {
            override fun onConnected(connectedController: CarTaskViewController) {
                dispatchToMain {
                    if (released) {
                        connectedController.release()
                        return@dispatchToMain
                    }
                    controller = connectedController
                    controllerRequestInFlight = false
                    controllerRetryAttempt = 0
                    controllerTimeoutJob?.cancel()
                    controllerRetryJob?.cancel()
                    Timber.tag(TAG).i("CarTaskViewController connected")
                    createTaskViewIfPossible()
                }
            }

            override fun onDisconnected(disconnectedController: CarTaskViewController) {
                dispatchToMain {
                    if (controller === disconnectedController) controller = null
                    controllerRequestInFlight = false
                    releaseTaskView()
                    if (!released) {
                        mutableEvents.tryEmit(HomeTaskViewEvent.Recovering)
                        scheduleControllerRetry()
                    }
                }
            }
        }

    private fun requestController() {
        val manager = carActivityManager
        if (released || controller != null || controllerRequestInFlight) return
        if (manager == null) {
            scheduleControllerRetry()
            return
        }
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
        }.onFailure { throwable ->
            controllerRequestInFlight = false
            Timber.tag(TAG).w(throwable, "Unable to request CarTaskViewController")
            scheduleControllerRetry()
        }
    }

    private fun scheduleControllerRetry() {
        if (!canRetryController()) return
        val retryDelay = retryDelayMillis(controllerRetryAttempt++)
        controllerRetryJob =
            scope.launch {
                delay(retryDelay)
                controllerRetryJob = null
                if (!released && controller == null) requestController()
            }
    }

    private fun canRetryController(): Boolean {
        if (released || requestedTarget == null) return false
        return controller == null && !controllerRequestInFlight && controllerRetryJob?.isActive != true
    }

    private fun armControllerTimeout() {
        controllerTimeoutJob?.cancel()
        controllerTimeoutJob =
            scope.launch {
                delay(CONTROLLER_CONNECT_TIMEOUT_MS)
                if (!released && controller == null) {
                    controllerRequestInFlight = false
                    reportError(
                        "TaskView unavailable",
                        "CarSystemUI did not register the car activity proxy on this device.",
                    )
                    scheduleControllerRetry()
                }
            }
    }

    private fun createTaskViewIfPossible() {
        if (!canCreateTaskView()) return
        val connectedController = requireNotNull(controller)
        val target = requireNotNull(requestedTarget)

        runCatching {
            connectedController.createControlledRemoteCarTaskView(
                ControlledRemoteCarTaskViewConfig
                    .Builder()
                    .setActivityIntent(target.toIntent())
                    .setShouldAutoRestartOnTaskRemoval(Build.TYPE == "user")
                    .build(),
                executor,
                taskCallback,
            )
        }.onFailure { throwable ->
            reportError(
                "TaskView creation failed",
                throwable.message ?: "Unable to create embedded surface.",
            )
            scheduleTaskViewRecreate()
        }
    }

    private fun canCreateTaskView(): Boolean {
        val hasControllerAndTarget = controller != null && requestedTarget != null
        return !released && hasControllerAndTarget && taskView == null
    }

    private fun replaceActivity(target: HomeEmbeddedTaskTarget) {
        awaitingReplacement = true
        runCatching {
            taskView?.setTaskVisibility(true)
            taskView?.replaceActivityIntent(target.toIntent())
            armTaskTimeout(target)
            Timber.tag(TAG).d("Replacing embedded task with %s", target.componentName)
        }.onFailure { throwable ->
            reportError(
                "Cannot embed ${target.label}",
                throwable.message ?: "The activity rejected embedding.",
            )
            scheduleTaskViewRecreate()
        }
    }

    private val taskCallback =
        object : ControlledRemoteCarTaskViewCallback {
            override fun onTaskViewCreated(created: ControlledRemoteCarTaskView) {
                dispatchToMain {
                    if (released) {
                        created.release()
                        return@dispatchToMain
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
                    requestedTarget?.let(::armTaskTimeout)
                    Timber.tag(TAG).i("ControlledRemoteCarTaskView created")
                }
            }

            override fun onTaskAppeared(taskInfo: ActivityManager.RunningTaskInfo) {
                // The platform callback enters through a binder thread. Cancel the watchdog
                // before dispatching UI work so a slow main looper cannot report a false timeout
                // after SystemUI has already delivered the task.
                cancelTaskTimeout()
                dispatchToMain {
                    if (released) return@dispatchToMain
                    cancelTaskTimeout()
                    awaitingReplacement = false
                    embeddedTaskId = taskInfo.taskId
                    taskView?.updateWindowBounds()
                    taskComponent(taskInfo)?.let { component ->
                        mutableEvents.tryEmit(
                            HomeTaskViewEvent.TaskAppeared(taskInfo.taskId, component),
                        )
                    } ?: reportError(
                        "Unknown embedded activity",
                        "The vehicle returned no embedded component.",
                    )
                }
            }

            override fun onTaskInfoChanged(taskInfo: ActivityManager.RunningTaskInfo) {
                cancelTaskTimeout()
                dispatchToMain {
                    if (released) return@dispatchToMain
                    taskView?.updateWindowBounds()
                    embeddedTaskId = taskInfo.taskId
                    taskComponent(taskInfo)?.let { component ->
                        mutableEvents.tryEmit(
                            HomeTaskViewEvent.TaskInfoChanged(taskInfo.taskId, component),
                        )
                    }
                }
            }

            override fun onTaskViewInitialized() {
                dispatchToMain {
                    Timber.tag(TAG).i("ControlledRemoteCarTaskView initialized")
                }
            }

            override fun onTaskVanished(taskInfo: ActivityManager.RunningTaskInfo) {
                dispatchToMain {
                    if (embeddedTaskId == taskInfo.taskId) embeddedTaskId = null
                    if (awaitingReplacement || released) return@dispatchToMain
                    val target = requestedTarget ?: return@dispatchToMain
                    if (target.type == HomeEmbeddedTargetType.NAVIGATION) {
                        runCatching { taskView?.startActivity() }
                            .onFailure {
                                reportError("Navigation closed", "Unable to restart navigation.")
                                scheduleTaskViewRecreate()
                            }
                    } else {
                        reportError("Embedded app closed", "${target.label} left the embedded task.")
                    }
                }
            }

            override fun onTaskViewReleased() {
                dispatchToMain {
                    releaseTaskView(releaseRemote = false)
                    if (!released) scheduleTaskViewRecreate()
                    Timber.tag(TAG).d("ControlledRemoteCarTaskView released by car service")
                }
            }
        }

    /** CarSystemUI may invoke TaskView callbacks from its binder thread; all view calls stay main. */
    private fun dispatchToMain(block: () -> Unit) {
        // Do not bind callback delivery to the host scope: a callback can race release and may
        // carry a remote controller/task view that must be released on the main thread. The
        // callback adapter owns this executor hop; lifecycle-bound Flow collectors still use the
        // structured child scope above.
        activity.mainExecutor.execute(block)
    }

    private fun taskComponent(taskInfo: ActivityManager.RunningTaskInfo): String? =
        taskInfo.baseIntent.component?.flattenToString()
            ?: taskInfo.topActivity?.flattenToString()

    private fun armTaskTimeout(target: HomeEmbeddedTaskTarget) {
        cancelTaskTimeout()
        taskTimeoutJob =
            scope.launch {
                delay(TASK_APPEAR_TIMEOUT_MS)
                if (!released && requestedTarget?.componentName == target.componentName) {
                    reportError(
                        "Embedding timed out",
                        "${target.label} did not appear in the embedded pane.",
                    )
                    scheduleTaskViewRecreate()
                }
            }
    }

    private fun scheduleTaskViewRecreate() {
        if (released || taskRecreateJob?.isActive == true) return
        taskRecreateJob =
            scope.launch {
                delay(TASK_VIEW_RECREATE_DELAY_MS)
                if (released) return@launch
                releaseTaskView()
                if (controller == null) {
                    requestController()
                } else {
                    createTaskViewIfPossible()
                }
            }
    }

    private fun restartForPackageChange(change: PackageChange) {
        val target = requestedTarget ?: return
        val packageName =
            ComponentName.unflattenFromString(target.componentName)?.packageName ?: return
        if (
            change.packageName == packageName ||
            change.packageName in taskViewRestartPackages
        ) {
            when (change.type) {
                PackageChange.Type.REMOVED ->
                    reportError("Navigation removed", "${target.label} is no longer installed.")
                PackageChange.Type.ADDED,
                PackageChange.Type.CHANGED,
                -> {
                    mutableEvents.tryEmit(HomeTaskViewEvent.Recovering)
                    scheduleTaskViewRecreate()
                }
            }
        }
    }

    private fun releaseTaskView(releaseRemote: Boolean = true) {
        cancelTaskTimeout()
        embeddedTaskId = null
        taskView?.let { remoteTaskView ->
            view.removeView(remoteTaskView)
            if (releaseRemote) remoteTaskView.release()
        }
        taskView = null
    }

    private fun cancelTaskTimeout() {
        taskTimeoutJob?.cancel()
        taskTimeoutJob = null
    }

    private fun reportError(
        title: String,
        message: String,
    ) {
        Timber.tag(TAG).w("%s: %s", title, message)
        mutableEvents.tryEmit(HomeTaskViewEvent.Failure(title, message))
    }

    private fun stringArrayResource(name: String): Array<String> {
        val id = activity.resources.getIdentifier(name, "array", activity.packageName)
        if (id == 0) return emptyArray()
        return activity.resources
            .getStringArray(id)
            .toList()
            .toTypedArray()
    }

    init {
        view.addOnLayoutChangeListener(layoutListener)
        runCatching {
            activity.enableTrustedOverlay(PRIVATE_FLAG_TRUSTED_OVERLAY)
            lifecycleOwner?.lifecycle?.addObserver(activityLifecycleObserver)
            scope.launch {
                carConnection.car.collectLatest { car ->
                    carActivityManager =
                        car?.let {
                            runCatching { it.getCarManager(CarActivityManager::class.java) }.getOrNull()
                        }
                    if (carActivityManager != null) requestController()
                }
            }
            scope.launch {
                packageChangeMonitor.packageChanges
                    .filter { change -> change.userId == currentUserId }
                    .collectLatest(::restartForPackageChange)
            }
        }.onFailure { throwable ->
            reportError("TaskView unavailable", throwable.message ?: "Unable to connect to car activity service.")
        }
    }

    private companion object {
        const val TAG = "CarLauncher.HomeTaskViewHost"
        const val CONTROLLER_CONNECT_TIMEOUT_MS = 10_000L
        const val TASK_APPEAR_TIMEOUT_MS = 8_000L
        const val TASK_VIEW_RECREATE_DELAY_MS = 400L
        const val HOST_INTERACTION_RESTORE_DELAY_MS = 100L
        const val FRAME_DELAY_MS = 16L
        const val PRIVATE_FLAG_TRUSTED_OVERLAY = 0x20000000

        fun retryDelayMillis(attempt: Int): Long =
            (FIRST_CONTROLLER_RETRY_DELAY_MS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT))
                .coerceAtMost(MAX_CONTROLLER_RETRY_DELAY_MS)

        const val FIRST_CONTROLLER_RETRY_DELAY_MS = 250L
        const val MAX_CONTROLLER_RETRY_DELAY_MS = 8_000L
        const val MAX_BACKOFF_SHIFT = 5
        const val PER_USER_RANGE = 100_000
    }
}

// Standalone Gradle builds cannot compile against this hidden Window method directly. The APK is
// platform-signed and updates a /system/priv-app, matching the stock launcher exemption.
@SuppressLint("SoonBlockedPrivateApi")
private fun Activity.enableTrustedOverlay(privateFlag: Int) {
    runCatching {
        Window::class.java
            .getDeclaredMethod("addPrivateFlags", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(window, privateFlag)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }.onFailure {
        Timber.tag("CarLauncher.HomeTaskViewHost").w(it, "Trusted TaskView overlay is unavailable")
    }
}

private fun HomeEmbeddedTaskTarget.toIntent(): Intent {
    val component =
        ComponentName.unflattenFromString(componentName)
            ?: error("Invalid target component: $componentName")
    val configuredIntent =
        launchIntentUri
            ?.let { serialized ->
                runCatching { Intent.parseUri(serialized, Intent.URI_INTENT_SCHEME) }.getOrNull()
            }
    return (configuredIntent ?: Intent(Intent.ACTION_MAIN)).apply {
        if (configuredIntent == null) {
            addCategory(
                if (this@toIntent.type == HomeEmbeddedTargetType.NAVIGATION) {
                    Intent.CATEGORY_APP_MAPS
                } else {
                    Intent.CATEGORY_LAUNCHER
                },
            )
        }
        setComponent(component)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
        )
    }
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
