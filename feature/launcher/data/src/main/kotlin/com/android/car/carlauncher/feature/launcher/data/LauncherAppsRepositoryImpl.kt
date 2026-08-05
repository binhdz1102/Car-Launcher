package com.android.car.carlauncher.feature.launcher.data

import android.car.Car
import android.car.content.pm.CarPackageManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedAppTarget
import com.android.car.carlauncher.feature.launcher.domain.EmbeddedTargetType
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

private val Context.launcherPreferences by preferencesDataStore("launcher_app_grid")
private val orderKey = stringPreferencesKey("ordered_components")

@Singleton
class LauncherAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LauncherAppsRepository {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val packageManager = context.packageManager
    private val currentUser = Process.myUserHandle()
    // LauncherApps exposes a Handler-based callback on this platform. It is kept only at the
    // Android callback boundary; all state propagation remains callbackFlow + Flow.
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val car = runCatching { Car.createCar(context) }.getOrNull()
    private val carPackageManager = runCatching {
        car?.getCarManager(CarPackageManager::class.java)
    }.getOrNull()
    private val uxRestrictionsManager = runCatching {
        car?.getCarManager(CarUxRestrictionsManager::class.java)
    }.getOrNull()

    override val launchableApps: Flow<List<LaunchableApp>> = combine(
        packageChanges(),
        uxRestrictionChanges(),
        context.launcherPreferences.data.map { it[orderKey].orEmpty() },
    ) { _, restrictionActive, savedOrder ->
        orderAndFilter(loadActivities(restrictionActive), savedOrder)
    }
        .conflate()
        .flowOn(Dispatchers.Default)

    override suspend fun navigationTarget(): Result<EmbeddedAppTarget> = runCatching {
        val mapIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
        val resolved = packageManager.resolveActivity(mapIntent, 0)
            ?: error("No navigation activity is installed for the current vehicle user.")
        val component = ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name)
        EmbeddedAppTarget(
            componentName = component.flattenToString(),
            label = resolved.loadLabel(packageManager).toString(),
            type = EmbeddedTargetType.NAVIGATION,
        )
    }.onFailure { Timber.tag(TAG).w(it, "Unable to resolve the default map activity") }

    override suspend fun saveOrderedComponents(componentNames: List<String>) {
        context.launcherPreferences.edit { preferences ->
            preferences[orderKey] = componentNames.joinToString("|")
        }
    }

    private fun packageChanges(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }
        }
        launcherApps.registerCallback(callback, callbackHandler)
        trySend(Unit)
        awaitClose { runCatching { launcherApps.unregisterCallback(callback) } }
    }

    private fun uxRestrictionChanges(): Flow<Boolean> = callbackFlow {
        val manager = uxRestrictionsManager
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
            trySend(restrictions.isRequiresDistractionOptimization)
        }
        runCatching {
            manager.registerListener(listener)
            trySend(manager.currentCarUxRestrictions?.isRequiresDistractionOptimization == true)
        }.onFailure {
            Timber.tag(TAG).w(it, "Car UX restrictions are unavailable")
            trySend(false)
        }
        awaitClose { runCatching { manager.unregisterListener() } }
    }

    private fun loadActivities(restrictionActive: Boolean): List<LaunchableApp> {
        val homePackage = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )?.activityInfo?.packageName

        return launcherApps.getActivityList(null, currentUser)
            .filter { info ->
                info.applicationInfo.packageName != context.packageName &&
                    info.applicationInfo.packageName != homePackage &&
                    info.applicationInfo.enabled &&
                    info.componentName.packageName !in HIDDEN_PACKAGES
            }
            .map { it.toLaunchableApp(restrictionActive) }
    }

    private fun LauncherActivityInfo.toLaunchableApp(restrictionActive: Boolean): LaunchableApp {
        val component = componentName
        val distractionOptimized = runCatching {
            carPackageManager?.isActivityDistractionOptimized(
                component.packageName,
                component.className,
            ) ?: true
        }.getOrDefault(false)
        val embeddingBlockReason = UNEMBEDDABLE_COMPONENTS[component.flattenToString()]
        val enabled = embeddingBlockReason == null && (!restrictionActive || distractionOptimized)
        return LaunchableApp(
            componentName = component.flattenToString(),
            packageName = component.packageName,
            label = label.toString(),
            isDistractionOptimized = distractionOptimized,
            isEnabled = enabled,
            disabledReason = embeddingBlockReason ?: if (enabled) {
                null
            } else {
                "Unavailable while driving because this app is not distraction optimized."
            },
        )
    }

    private fun orderAndFilter(
        apps: List<LaunchableApp>,
        serializedOrder: String,
    ): List<LaunchableApp> {
        val indexes = serializedOrder.split('|')
            .filter(String::isNotBlank)
            .withIndex()
            .associate { (index, value) -> value to index }
        return apps.sortedWith(
            compareBy<LaunchableApp> { indexes[it.componentName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase() },
        )
    }

    private companion object {
        const val TAG = "CarLauncher.AppsRepository"
        val HIDDEN_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.android.systemui",
        )
        val UNEMBEDDABLE_COMPONENTS = mapOf(
            "com.android.car.settings/com.android.car.settings.Settings_Launcher_Homepage" to
                "AAOS Settings redirects to a separate task on this system image.",
        )
    }
}
