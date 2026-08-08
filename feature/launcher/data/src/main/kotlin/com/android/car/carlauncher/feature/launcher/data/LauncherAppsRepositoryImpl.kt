package com.android.car.carlauncher.feature.launcher.data

import android.app.ActivityOptions
import android.car.content.pm.CarPackageManager
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.media.CarMediaIntents
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.service.media.MediaBrowserService
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.car.carlauncher.core.common.CarServiceConnection
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedAppTarget
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTargetType
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LaunchableAppDisabledReason
import com.android.car.carlauncher.feature.launcher.domain.LaunchableAppType
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.LauncherRestrictions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.launcherPreferences by preferencesDataStore("launcher_app_grid")
private val orderKey = stringPreferencesKey("ordered_components")

@Suppress("DEPRECATION")
private fun PackageManager.resolveActivityCompat(
    intent: Intent,
    flags: Int,
): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        resolveActivity(intent, flags)
    }

@Suppress("DEPRECATION")
private fun PackageManager.queryIntentServicesCompat(
    intent: Intent,
    flags: Int,
): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentServices(intent, flags)
    }

private fun isDistractionOptimized(
    carPackageManager: CarPackageManager,
    component: ComponentName,
): Boolean =
    runCatching {
        carPackageManager.isActivityDistractionOptimized(
            component.packageName,
            component.className,
        )
    }.getOrDefault(false)

private fun orderAndFilter(
    apps: List<LaunchableApp>,
    serializedOrder: String,
): List<LaunchableApp> {
    val indexes =
        serializedOrder
            .split('|')
            .filter(String::isNotBlank)
            .withIndex()
            .associate { (index, value) -> value to index }
    return apps.sortedWith(
        compareBy<LaunchableApp> { indexes[it.componentName] ?: Int.MAX_VALUE }
            .thenBy { it.label.lowercase() },
    )
}

private fun createLaunchableApp(
    component: ComponentName,
    label: String,
    type: LaunchableAppType,
    distractionOptimized: Boolean,
    currentRestrictions: LauncherRestrictions,
): LaunchableApp {
    val enabled =
        currentRestrictions.carServiceReady &&
            (!currentRestrictions.requiresDistractionOptimization || distractionOptimized)
    return LaunchableApp(
        componentName = component.flattenToString(),
        packageName = component.packageName,
        label = label,
        type = type,
        isDistractionOptimized = distractionOptimized,
        isEnabled = enabled,
        disabledReason =
            when {
                !currentRestrictions.carServiceReady ->
                    LaunchableAppDisabledReason.SAFETY_SERVICE_UNAVAILABLE
                !enabled -> LaunchableAppDisabledReason.NOT_DISTRACTION_OPTIMIZED
                else -> null
            },
    )
}

@Singleton
class LauncherAppsRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
    ) : LauncherAppsRepository {
        private val launcherApps = context.getSystemService(LauncherApps::class.java)
        private val packageManager = context.packageManager
        private val currentUser = Process.myUserHandle()
        private val callbackHandler = Handler(Looper.getMainLooper())
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        override val restrictions =
            restrictionChanges()
                .distinctUntilChanged()
                .stateIn(repositoryScope, SharingStarted.Eagerly, FAIL_SAFE_RESTRICTIONS)

        override val launchableApps: Flow<List<LaunchableApp>> =
            combine(
                packageChanges(),
                restrictions,
                context.launcherPreferences.data.map { it[orderKey].orEmpty() },
                carConnection.car,
            ) { _, currentRestrictions, savedOrder, _ ->
                orderAndFilter(loadApps(currentRestrictions), savedOrder)
            }.conflate()
                .flowOn(Dispatchers.Default)

        override suspend fun navigationTarget(): Result<EmbeddedAppTarget> =
            runCatching {
                val car = carConnection.car.filterNotNull().first()
                val carPackageManager =
                    car.getCarManager(CarPackageManager::class.java)
                        ?: error("Car package safety service is unavailable.")
                val mapIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
                val resolved =
                    packageManager.resolveActivityCompat(
                        mapIntent,
                        PackageManager.MATCH_DEFAULT_ONLY,
                    ) ?: error("No navigation activity is installed for the current vehicle user.")
                val component = ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name)
                if (restrictions.value.requiresDistractionOptimization) {
                    check(isDistractionOptimized(carPackageManager, component)) {
                        "The navigation activity is unavailable while driving."
                    }
                }
                EmbeddedAppTarget(
                    componentName = component.flattenToString(),
                    label = resolved.loadLabel(packageManager).toString(),
                    type = EmbeddedTargetType.NAVIGATION,
                )
            }.onFailure { Timber.tag(TAG).w(it, "Unable to resolve the default map activity") }

        override suspend fun launch(app: LaunchableApp): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    val current =
                        loadApps(restrictions.value)
                            .firstOrNull { it.componentName == app.componentName && it.type == app.type }
                            ?: error("The selected application is no longer available.")
                    check(current.isEnabled) { "The selected application is unavailable while driving." }
                    val component =
                        ComponentName.unflattenFromString(current.componentName)
                            ?: error("Invalid application component.")
                    when (current.type) {
                        LaunchableAppType.ACTIVITY ->
                            launcherApps.startMainActivity(
                                component,
                                currentUser,
                                null,
                                ActivityOptions.makeBasic().toBundle(),
                            )
                        LaunchableAppType.MEDIA_SERVICE ->
                            context.startActivity(
                                Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
                                    .putExtra(CarMediaIntents.EXTRA_MEDIA_COMPONENT, component.flattenToString())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                    }
                }.onFailure { Timber.tag(TAG).w(it, "Unable to launch %s", app.componentName) }
            }

        override suspend fun saveOrderedComponents(componentNames: List<String>) {
            context.launcherPreferences.edit { preferences ->
                preferences[orderKey] = componentNames.joinToString("|")
            }
        }

        override suspend fun clearOrderedComponents() {
            context.launcherPreferences.edit { preferences -> preferences.remove(orderKey) }
        }

        private fun packageChanges(): Flow<Unit> =
            callbackFlow {
                val callback =
                    object : LauncherApps.Callback() {
                        override fun onPackageAdded(
                            packageName: String,
                            user: android.os.UserHandle,
                        ) {
                            if (user == currentUser) trySend(Unit)
                        }

                        override fun onPackageChanged(
                            packageName: String,
                            user: android.os.UserHandle,
                        ) {
                            if (user == currentUser) trySend(Unit)
                        }

                        override fun onPackageRemoved(
                            packageName: String,
                            user: android.os.UserHandle,
                        ) {
                            if (user == currentUser) trySend(Unit)
                        }

                        override fun onPackagesAvailable(
                            packageNames: Array<String>,
                            user: android.os.UserHandle,
                            replacing: Boolean,
                        ) {
                            if (user == currentUser) trySend(Unit)
                        }

                        override fun onPackagesUnavailable(
                            packageNames: Array<String>,
                            user: android.os.UserHandle,
                            replacing: Boolean,
                        ) {
                            if (user == currentUser) trySend(Unit)
                        }
                    }
                launcherApps.registerCallback(callback, callbackHandler)
                trySend(Unit)
                awaitClose { runCatching { launcherApps.unregisterCallback(callback) } }
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun restrictionChanges(): Flow<LauncherRestrictions> =
            carConnection.car
                .flatMapLatest { car ->
                    if (car == null) {
                        flowOf(FAIL_SAFE_RESTRICTIONS)
                    } else {
                        val manager =
                            runCatching {
                                car.getCarManager(CarUxRestrictionsManager::class.java)
                            }.getOrNull()
                        if (manager == null) {
                            flowOf(FAIL_SAFE_RESTRICTIONS)
                        } else {
                            callbackFlow {
                                val listener =
                                    CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
                                        trySend(it.toLauncherRestrictions())
                                    }
                                runCatching {
                                    manager.registerListener(listener)
                                    trySend(manager.currentCarUxRestrictions.toLauncherRestrictions())
                                }.onFailure {
                                    Timber.tag(TAG).w(it, "Car UX restrictions are unavailable")
                                    trySend(FAIL_SAFE_RESTRICTIONS)
                                }
                                awaitClose { runCatching { manager.unregisterListener() } }
                            }
                        }
                    }
                }

        private fun CarUxRestrictions?.toLauncherRestrictions(): LauncherRestrictions {
            if (this == null) return FAIL_SAFE_RESTRICTIONS
            return LauncherRestrictions(
                requiresDistractionOptimization = isRequiresDistractionOptimization,
                noKeyboard = activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD != 0,
                carServiceReady = true,
            )
        }

        private fun loadApps(currentRestrictions: LauncherRestrictions): List<LaunchableApp> {
            val carPackageManager =
                carConnection.car.value?.let { car ->
                    runCatching { car.getCarManager(CarPackageManager::class.java) }.getOrNull()
                }
            val mediaServices = loadMediaServices(currentRestrictions)
            val mediaPackages = mediaServices.mapTo(mutableSetOf()) { it.packageName }
            return launcherApps
                .getActivityList(null, currentUser)
                .asSequence()
                .filter { info ->
                    info.applicationInfo.packageName != context.packageName &&
                        info.applicationInfo.enabled &&
                        info.componentName.packageName !in HIDDEN_PACKAGES &&
                        info.componentName.packageName !in mediaPackages
                }.map { it.toLaunchableApp(currentRestrictions, carPackageManager) }
                .plus(mediaServices)
                .toList()
        }

        private fun loadMediaServices(currentRestrictions: LauncherRestrictions): List<LaunchableApp> =
            packageManager
                .queryIntentServicesCompat(
                    Intent(MediaBrowserService.SERVICE_INTERFACE),
                    PackageManager.MATCH_ALL,
                ).asSequence()
                .mapNotNull { it.serviceInfo }
                .filter { it.enabled && it.exported && it.packageName !in HIDDEN_PACKAGES }
                .map { it.toMediaApp(currentRestrictions) }
                .distinctBy(LaunchableApp::componentName)
                .toList()

        private fun LauncherActivityInfo.toLaunchableApp(
            currentRestrictions: LauncherRestrictions,
            carPackageManager: CarPackageManager?,
        ): LaunchableApp {
            val component = componentName
            val distractionOptimized =
                carPackageManager?.let {
                    isDistractionOptimized(it, component)
                } == true
            return createLaunchableApp(
                component = component,
                label = label.toString(),
                type = LaunchableAppType.ACTIVITY,
                distractionOptimized = distractionOptimized,
                currentRestrictions = currentRestrictions,
            )
        }

        private fun ServiceInfo.toMediaApp(currentRestrictions: LauncherRestrictions): LaunchableApp =
            createLaunchableApp(
                component = ComponentName(packageName, name),
                label = loadLabel(packageManager).toString(),
                type = LaunchableAppType.MEDIA_SERVICE,
                distractionOptimized = true,
                currentRestrictions = currentRestrictions,
            )

        private companion object {
            const val TAG = "CarLauncher.AppsRepository"
            val FAIL_SAFE_RESTRICTIONS = LauncherRestrictions()
            val HIDDEN_PACKAGES =
                setOf(
                    "com.android.permissioncontroller",
                    "com.android.systemui",
                )
        }
    }
