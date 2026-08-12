package com.android.car.carlauncher.feature.launcher.data

import android.app.ActivityManager
import android.app.TaskInfo
import android.car.content.pm.CarPackageManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.core.platform.QuickStepRecentTasksSession
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.RecentTask
import com.android.car.carlauncher.feature.launcher.domain.RecentTasksRepository
import com.android.systemui.shared.system.ActivityManagerWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentTasksRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
        private val launcherAppsRepository: LauncherAppsRepository,
    ) : RecentTasksRepository {
        private val activityManager = context.getSystemService(ActivityManager::class.java)
        private val activityManagerWrapper = ActivityManagerWrapper.getInstance()
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutableTasks = MutableStateFlow(emptyList<RecentTask>())
        private var currentDisplayId = 0

        override val tasks: StateFlow<List<RecentTask>> = mutableTasks.asStateFlow()

        init {
            repositoryScope.launch {
                launcherAppsRepository.restrictions.drop(1).collectLatest {
                    refresh(currentDisplayId)
                }
            }
        }

        override suspend fun refresh(displayId: Int) =
            withContext(Dispatchers.IO) {
                currentDisplayId = displayId
                mutableTasks.value = readTasks(displayId)
            }

        override suspend fun open(task: RecentTask): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    check(task.isEnabled) { "Task is unavailable while driving." }
                    check(activityManagerWrapper.startActivityFromRecents(task.taskId, null)) {
                        "Activity manager rejected the recent task."
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "Unable to open recent task=%d", task.taskId)
                }
            }

        override suspend fun openTopRunningTask(displayId: Int): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val recentsComponent = ComponentName(context, RECENTS_CLASS_NAME)
                    val runningTasks = activityManagerWrapper.getRunningTasks(false, displayId)
                    val candidate =
                        runningTasks
                            .asSequence()
                            .dropWhile { task -> task.topComponent() != recentsComponent }
                            .drop(1)
                            .firstOrNull()
                            ?: error("No task was running behind Recents.")
                    val component =
                        candidate.topComponent()
                            ?: error("The running task has no component.")
                    check(isAllowedNow(component)) { "Task is unavailable while driving." }
                    check(activityManagerWrapper.startActivityFromRecents(candidate.taskId, null)) {
                        "Activity manager rejected the running task."
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "Unable to open task behind Recents")
                }
            }

        override suspend fun remove(task: RecentTask): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    activityManagerWrapper.removeTask(task.taskId)
                    refresh(currentDisplayId)
                }.onFailure {
                    Timber.tag(TAG).w(it, "Unable to remove recent task=%d", task.taskId)
                }
            }

        override suspend fun clearAll(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    activityManagerWrapper.removeAllRecentTasks()
                    refresh(currentDisplayId)
                }.onFailure { Timber.tag(TAG).w(it, "Unable to clear recent tasks") }
            }

        @Suppress("DEPRECATION")
        private fun readTasks(displayId: Int): List<RecentTask> {
            val taskInfos =
                QuickStepRecentTasksSession.read(MAX_TASKS, Process.myUid() / PER_USER_RANGE)
                    ?: activityManager.getRecentTasks(
                        MAX_TASKS,
                        ActivityManager.RECENT_IGNORE_UNAVAILABLE,
                    )
            return taskInfos
                .asSequence()
                .filter { it.displayIdCompat() == displayId }
                .mapNotNull { it.toRecentTask() }
                .distinctBy(RecentTask::taskId)
                .toList()
        }

        private fun TaskInfo.toRecentTask(): RecentTask? =
            topComponent()
                ?.takeUnless { component ->
                    component.packageName == context.packageName ||
                        component.packageName in HIDDEN_PACKAGES
                }?.let { component ->
                    val packageName = component.packageName
                    val label =
                        runCatching {
                            context.packageManager
                                .getActivityInfo(component, 0)
                                .loadLabel(context.packageManager)
                                .toString()
                        }.getOrElse { applicationLabel(packageName) }
                    RecentTask(
                        taskId = taskId,
                        componentName = component.flattenToString(),
                        packageName = packageName,
                        label = label,
                        thumbnailBytes = taskThumbnail(taskId),
                        isEnabled = isAllowedNow(component),
                    )
                }

        private fun isAllowedNow(component: ComponentName): Boolean {
            val restrictions = launcherAppsRepository.restrictions.value
            return when {
                !restrictions.carServiceReady -> false
                !restrictions.requiresDistractionOptimization -> true
                else -> {
                    val manager =
                        carConnection.car.value?.let { car ->
                            runCatching {
                                car.getCarManager(CarPackageManager::class.java)
                            }.getOrNull()
                        }
                    manager != null &&
                        runCatching {
                            manager.isActivityDistractionOptimized(
                                component.packageName,
                                component.className,
                            )
                        }.getOrDefault(false)
                }
            }
        }

        private fun taskThumbnail(taskId: Int): ByteArray? =
            runCatching {
                // Automotive builds persist low-resolution task snapshots. Asking only for the
                // high-resolution variant returns ThumbnailData's empty fallback on this AVD.
                val bitmap = activityManagerWrapper.getTaskThumbnail(taskId, true).thumbnail
                if (bitmap == null) {
                    Timber.tag(TAG).d("No task snapshot returned for task=%d", taskId)
                    null
                } else {
                    Timber.tag(TAG).d(
                        "Task snapshot task=%d size=%dx%d",
                        taskId,
                        bitmap.width,
                        bitmap.height,
                    )
                    bitmap
                        .takeIf { it.width > 1 && it.height > 1 }
                        ?.toJpegBytes(THUMBNAIL_QUALITY)
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "Unable to read task snapshot=%d", taskId)
            }.getOrNull()

        private fun applicationLabel(packageName: String): String =
            runCatching {
                context.packageManager
                    .getApplicationLabel(
                        context.packageManager.getApplicationInfo(packageName, 0),
                    ).toString()
            }.getOrDefault(packageName)

        private companion object {
            const val TAG = "CarLauncher.RecentTasksRepository"
            const val MAX_TASKS = 30
            const val THUMBNAIL_QUALITY = 82
            const val PER_USER_RANGE = 100_000
            const val RECENTS_CLASS_NAME =
                "com.android.car.carlauncher.recents.CarRecentsActivity"
            val HIDDEN_PACKAGES =
                setOf(
                    "com.android.systemui",
                    "com.android.permissioncontroller",
                )
        }
    }

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
