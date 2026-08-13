package com.android.car.carlauncher.feature.recents.data

import android.app.ActivityManager
import android.app.TaskInfo
import android.car.content.pm.CarPackageManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.core.platform.DrivingRestrictionMonitor
import com.android.car.carlauncher.core.platform.QuickStepRecentTasksSession
import com.android.car.carlauncher.core.platform.UxrState
import com.android.car.carlauncher.feature.recents.domain.RecentTask
import com.android.car.carlauncher.feature.recents.domain.RecentsRepository
import com.android.car.carlauncher.feature.recents.domain.RecentsState
import com.android.car.carlauncher.feature.recents.domain.RecentsStateReducer
import com.android.systemui.shared.system.ActivityManagerWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Privileged ActivityTaskManager and QuickStep adapter for the AAOS Recents feature. */
@Suppress("TooManyFunctions")
@Singleton
class AndroidRecentsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
        private val drivingRestrictions: DrivingRestrictionMonitor,
    ) : RecentsRepository {
        private val activityManager = context.getSystemService(ActivityManager::class.java)
        private val activityManagerWrapper = ActivityManagerWrapper.getInstance()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val display = MutableStateFlow(DEFAULT_DISPLAY)
        private val mutableState = MutableStateFlow(RecentsState())

        private val platformSignals: StateFlow<PlatformSignal> =
            combine(
                display,
                drivingRestrictions.restrictions,
                QuickStepRecentTasksSession.recentTasks,
            ) { currentDisplay, restriction, _ ->
                PlatformSignal(currentDisplay, restriction)
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PlatformSignal(DEFAULT_DISPLAY, UxrState.Unavailable),
            )

        override val state: StateFlow<RecentsState> = mutableState

        init {
            scope.launch {
                platformSignals.collectLatest { signal ->
                    mutableState.value = readState(signal.display, signal.restriction)
                }
            }
        }

        override suspend fun refresh(display: DisplayTarget) {
            this.display.value = display
            mutableState.value = readState(display, drivingRestrictions.restrictions.value)
        }

        override suspend fun launch(task: RecentTask): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    check(task.isEnabled) { "Task is unavailable while driving." }
                    check(activityManagerWrapper.startActivityFromRecents(task.taskId, null)) {
                        "Activity manager rejected the recent task."
                    }
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Unable to open recent task=%d", task.taskId)
                }
            }

        override suspend fun launchTopRunningTask(display: DisplayTarget): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val recentsComponent = ComponentName(context, RECENTS_ACTIVITY_CLASS)
                    val candidate =
                        activityManagerWrapper
                            .getRunningTasks(false, display.displayId)
                            .asSequence()
                            .dropWhile { task -> task.topComponent() != recentsComponent }
                            .drop(1)
                            .firstOrNull()
                            ?: error("No task was running behind Recents.")
                    val component = candidate.topComponent() ?: error("The running task has no component.")
                    check(isAllowed(component, drivingRestrictions.restrictions.value)) {
                        "Task is unavailable while driving."
                    }
                    check(activityManagerWrapper.startActivityFromRecents(candidate.taskId, null)) {
                        "Activity manager rejected the running task."
                    }
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Unable to open task behind Recents")
                }
            }

        override suspend fun dismiss(task: RecentTask): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    activityManagerWrapper.removeTask(task.taskId)
                    mutableState.value = readState(display.value, drivingRestrictions.restrictions.value)
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Unable to remove recent task=%d", task.taskId)
                }
            }

        override suspend fun clearAll(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    activityManagerWrapper.removeAllRecentTasks()
                    mutableState.value = RecentsState(tasks = emptyList(), display = display.value, isLoading = false)
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Unable to clear recent tasks")
                }
            }

        @Suppress("DEPRECATION")
        private fun readState(
            display: DisplayTarget,
            restriction: UxrState,
        ): RecentsState {
            val taskInfos =
                QuickStepRecentTasksSession.read(MAX_TASKS, Process.myUid() / PER_USER_RANGE)
                    ?: activityManager.getRecentTasks(MAX_TASKS, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            val tasks =
                taskInfos
                    .asSequence()
                    .filter { task -> task.displayIdCompat() == display.displayId }
                    .mapNotNull { task -> task.toRecentTask(restriction) }
                    .toList()
            return RecentsState(
                tasks = RecentsStateReducer.stableTasks(tasks),
                display = display,
                isLoading = false,
            )
        }

        private fun TaskInfo.toRecentTask(restriction: UxrState): RecentTask? =
            topComponent()
                ?.takeUnless { component ->
                    component.packageName == context.packageName || component.packageName in hiddenPackages()
                }?.let { component ->
                    RecentTask(
                        taskId = taskId,
                        componentName = component.flattenToString(),
                        packageName = component.packageName,
                        label = applicationLabel(component),
                        thumbnailBytes = taskThumbnail(taskId),
                        restriction = restriction.level,
                        isEnabled = isAllowed(component, restriction),
                    )
                }

        private fun isAllowed(
            component: ComponentName,
            restriction: UxrState,
        ): Boolean =
            when {
                !restriction.serviceAvailable -> false
                !restriction.requiresDistractionOptimization -> true
                else -> carPackageManager().isDistractionOptimized(component)
            }

        private fun carPackageManager(): CarPackageManager? =
            carConnection.car.value?.let { car ->
                runCatching { car.getCarManager(CarPackageManager::class.java) }.getOrNull()
            }

        private fun CarPackageManager?.isDistractionOptimized(component: ComponentName): Boolean =
            this?.let { manager ->
                runCatching {
                    manager.isActivityDistractionOptimized(component.packageName, component.className)
                }.getOrDefault(false)
            } == true

        private fun taskThumbnail(taskId: Int): ByteArray? =
            runCatching {
                val bitmap = activityManagerWrapper.getTaskThumbnail(taskId, true).thumbnail
                bitmap
                    ?.takeIf { it.width > 1 && it.height > 1 }
                    ?.toJpegBytes(THUMBNAIL_QUALITY)
            }.onFailure { throwable ->
                Timber.tag(TAG).w(throwable, "Unable to read task snapshot=%d", taskId)
            }.getOrNull()

        private fun applicationLabel(component: ComponentName): String =
            runCatching {
                context.packageManager
                    .getActivityInfo(component, 0)
                    .loadLabel(context.packageManager)
                    .toString()
            }.getOrElse { applicationLabel(component.packageName) }

        private fun applicationLabel(packageName: String): String =
            runCatching {
                context.packageManager
                    .getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0))
                    .toString()
            }.getOrDefault(packageName)

        private fun hiddenPackages(): Set<String> {
            val resourceId =
                context.resources.getIdentifier(
                    "packages_hidden_from_recents",
                    "array",
                    context.packageName,
                )
            val configured = resourceId.takeIf { it != 0 }?.let(context.resources::getStringArray).orEmpty()
            return configured.toSet() + DEFAULT_HIDDEN_PACKAGES
        }

        private companion object {
            const val TAG = "CarLauncher.Recents"
            const val MAX_TASKS = 30
            const val THUMBNAIL_QUALITY = 82
            const val PER_USER_RANGE = 100_000
            const val RECENTS_ACTIVITY_CLASS = "com.android.car.carlauncher.recents.CarRecentsActivity"
            val DEFAULT_DISPLAY = DisplayTarget(displayId = 0, isDefaultDisplay = true)
            val DEFAULT_HIDDEN_PACKAGES = setOf("com.android.systemui", "com.android.permissioncontroller")
        }
    }

private data class PlatformSignal(
    val display: DisplayTarget,
    val restriction: UxrState,
)

private fun TaskInfo.topComponent(): ComponentName? = topActivity ?: baseIntent.component

private fun TaskInfo.displayIdCompat(): Int =
    runCatching {
        javaClass.getMethod("getDisplayId").invoke(this) as Int
    }.getOrDefault(0)

private fun Bitmap.toJpegBytes(quality: Int): ByteArray =
    ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        output.toByteArray()
    }
