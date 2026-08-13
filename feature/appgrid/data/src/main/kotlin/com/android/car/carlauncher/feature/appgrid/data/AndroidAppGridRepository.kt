package com.android.car.carlauncher.feature.appgrid.data

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.usage.UsageStatsManager
import android.car.content.pm.CarPackageManager
import android.car.media.CarMediaIntents
import android.car.media.CarMediaManager
import android.car.settings.CarSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.service.media.MediaBrowserService
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.core.platform.DrivingRestrictionMonitor
import com.android.car.carlauncher.core.platform.PackageChangeMonitor
import com.android.car.carlauncher.core.platform.UxrState
import com.android.car.carlauncher.feature.appgrid.domain.AppGridAvailability
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItem
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItemType
import com.android.car.carlauncher.feature.appgrid.domain.AppGridMode
import com.android.car.carlauncher.feature.appgrid.domain.AppGridOrientation
import com.android.car.carlauncher.feature.appgrid.domain.AppGridRepository
import com.android.car.carlauncher.feature.appgrid.domain.AppGridShortcut
import com.android.car.carlauncher.feature.appgrid.domain.AppGridState
import com.android.car.carlauncher.feature.appgrid.domain.AppGridStateReducer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.reflect.Method
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** AAOS App Grid repository: package discovery, UXR/TOS/mirroring policy and dual-write order. */
@Singleton
@Suppress("TooManyFunctions") // The platform boundary deliberately keeps AAOS adapter calls together.
class AndroidAppGridRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val carConnection: CarServiceConnection,
        private val drivingRestrictions: DrivingRestrictionMonitor,
        private val packageChanges: PackageChangeMonitor,
        private val orderStore: AppGridOrderStore,
        private val mirroringSessions: MirroringSessionObserver,
    ) : AppGridRepository {
        private val launcherApps = context.getSystemService(LauncherApps::class.java)
        private val packageManager = context.packageManager
        private val user = Process.myUserHandle()
        private val userId = Process.myUid() / PER_USER_RANGE
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val order = MutableStateFlow(emptyList<LauncherComponent>())
        private val mediaTemplateMethod: Method? by lazy {
            runCatching {
                val mediaContext =
                    context.createPackageContext(
                        MEDIA_PACKAGE,
                        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
                    )
                mediaContext.classLoader
                    .loadClass(MEDIA_SOURCE_CLASS)
                    .getMethod("isMediaTemplate", Context::class.java, ComponentName::class.java)
            }.onFailure { throwable ->
                Timber.tag(TAG).w(throwable, "CarMediaApp media-template API unavailable")
            }.getOrNull()
        }

        private val inventoryChanges: Flow<Unit> =
            combine(
                packageChanges.packageChanges
                    .filter { change -> change.userId == userId }
                    .map { Unit }
                    .onStart { emit(Unit) },
                secureSettingChanges(),
            ) { _, _ -> Unit }.conflate()

        override val state: StateFlow<AppGridState> =
            combine(
                inventoryChanges,
                drivingRestrictions.restrictions,
                carConnection.car,
                order,
                mirroringSessions.sessions,
            ) { _, uxr, car, savedOrder, mirroring ->
                buildState(uxr, car, savedOrder, mirroring)
            }.stateIn(scope, SharingStarted.Eagerly, initialState())

        init {
            scope.launch { order.value = orderStore.read() }
        }

        override suspend fun reorder(
            fromIndex: Int,
            toIndex: Int,
        ): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    check(state.value.canReorder) { "App order cannot be changed while driving." }
                    val reordered = state.value.items.toMutableList()
                    require(fromIndex in reordered.indices && toIndex in reordered.indices) {
                        "Invalid app-grid positions."
                    }
                    reordered.add(toIndex, reordered.removeAt(fromIndex))
                    val nextOrder = reordered.map(AppGridItem::component)
                    orderStore.write(nextOrder)
                    order.value = nextOrder
                }
            }

        override suspend fun saveOrder(order: List<LauncherComponent>): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    check(state.value.canReorder) { "App order cannot be changed while driving." }
                    val existing =
                        state.value.items
                            .map(AppGridItem::component)
                            .toSet()
                    val canonical = order.filter { component -> component in existing }.distinct()
                    check(canonical.size == existing.size) { "The reordered app list is incomplete." }
                    orderStore.write(canonical)
                    this@AndroidAppGridRepository.order.value = canonical
                }
            }

        override suspend fun clearOrder(): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    orderStore.clear()
                    order.value = emptyList()
                }
            }

        override suspend fun launch(
            item: AppGridItem,
            display: DisplayTarget,
            mode: AppGridMode,
        ): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    when (item.availability) {
                        AppGridAvailability.AVAILABLE -> launchAvailable(item, display, mode)
                        AppGridAvailability.TOS_REVIEW_REQUIRED -> reviewTos(display).getOrThrow()
                        AppGridAvailability.REQUIRES_DISTRACTION_OPTIMIZATION ->
                            error("The selected app is unavailable while driving.")
                        AppGridAvailability.CAR_SERVICE_UNAVAILABLE ->
                            error("Vehicle safety service is unavailable.")
                    }
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Unable to launch %s", item.component.flattened)
                }
            }

        override suspend fun shortcuts(item: AppGridItem): Result<List<AppGridShortcut>> =
            withContext(Dispatchers.Default) {
                runCatching {
                    if (item.type != AppGridItemType.ACTIVITY || item.availability != AppGridAvailability.AVAILABLE) {
                        return@runCatching emptyList()
                    }
                    launcherApps
                        .getShortcuts(
                            LauncherApps
                                .ShortcutQuery()
                                .setPackage(item.component.packageName)
                                .setQueryFlags(
                                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                                ),
                            user,
                        ).orEmpty()
                        .mapNotNull { shortcut ->
                            shortcut.shortLabel?.toString()?.takeIf(String::isNotBlank)?.let { shortLabel ->
                                AppGridShortcut(shortcut.id, shortLabel, shortcut.longLabel?.toString())
                            }
                        }
                }
            }

        override suspend fun launchShortcut(
            item: AppGridItem,
            shortcut: AppGridShortcut,
            display: DisplayTarget,
        ): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    launcherApps.startShortcut(
                        item.component.packageName,
                        shortcut.id,
                        null,
                        optionsFor(display),
                        user,
                    )
                }
            }

        override suspend fun dismissTosBanner() {
            withContext(Dispatchers.Default) {
                legacyPreferences()
                    .edit()
                    .putLong(TOS_BANNER_DISMISS_TIME_KEY, Instant.now().epochSecond)
                    .apply()
            }
        }

        override suspend fun reviewTos(display: DisplayTarget): Result<Unit> =
            withContext(Dispatchers.Default) {
                runCatching {
                    startTosActivity(display)
                }
            }

        private fun buildState(
            uxr: UxrState,
            car: android.car.Car?,
            savedOrder: List<LauncherComponent>,
            mirroring: MirroringSession,
        ): AppGridState {
            val tos = readTosState()
            val discovered = discoverApps(uxr, car, tos, mirroring)
            val ordered = AppGridStateReducer.arrange(discovered, savedOrder.map(LauncherComponent::flattened))
            return AppGridState(
                items = ordered,
                orientation = appGridOrientation(),
                restriction = uxr.level,
                tosAccepted = tos.accepted,
                shouldShowTosBanner = tos.blocksApps && shouldShowTosBanner(),
                canReorder = AppGridStateReducer.canReorder(uxr.level, query = "", mode = AppGridMode.ALL_APPS),
            )
        }

        private fun discoverApps(
            uxr: UxrState,
            car: android.car.Car?,
            tos: TosState,
            mirroring: MirroringSession,
        ): List<AppGridItem> {
            val carPackageManager =
                car?.let { connectedCar ->
                    runCatching { connectedCar.getCarManager(CarPackageManager::class.java) }.getOrNull()
                }
            val recentPackages = recentPackages()
            val media = mediaItems(uxr, carPackageManager, recentPackages, mirroring)
            val normal = normalItems(uxr, carPackageManager, recentPackages, mirroring)
            val knownPackages = (normal + media).map { it.component.packageName }.toSet()
            val disabled =
                restrictedItems(
                    setting = CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                    separator = RESOURCE_OVERUSE_SEPARATOR,
                    flags = PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
                    type = AppGridItemType.DISABLED_ACTIVITY,
                    uxr = uxr,
                    carPackageManager = carPackageManager,
                    recentPackages = recentPackages,
                    mirroring = mirroring,
                    knownPackages = knownPackages,
                )
            val tosItems =
                if (tos.blocksApps) {
                    restrictedItems(
                        setting = CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
                        separator = TOS_SEPARATOR,
                        flags = PackageManager.MATCH_DISABLED_COMPONENTS,
                        type = AppGridItemType.TOS_RESTRICTED_ACTIVITY,
                        uxr = uxr,
                        carPackageManager = carPackageManager,
                        recentPackages = recentPackages,
                        mirroring = mirroring,
                        knownPackages = knownPackages,
                    )
                } else {
                    emptyList()
                }
            return (normal + media + disabled + tosItems).filterNot(::isTelephonyAppForPassenger)
        }

        private fun mediaItems(
            uxr: UxrState,
            carPackageManager: CarPackageManager?,
            recentPackages: Set<String>,
            mirroring: MirroringSession,
        ): List<AppGridItem> =
            mediaServices().map { service ->
                service.toItem(
                    type = AppGridItemType.MEDIA_SERVICE,
                    uxr = uxr,
                    carPackageManager = carPackageManager,
                    recentPackages = recentPackages,
                    mirroring = mirroring,
                )
            }

        private fun normalItems(
            uxr: UxrState,
            carPackageManager: CarPackageManager?,
            recentPackages: Set<String>,
            mirroring: MirroringSession,
        ): List<AppGridItem> =
            launcherApps
                .getActivityList(null, user)
                .asSequence()
                .filter { activity ->
                    activity.applicationInfo.enabled &&
                        activity.componentName.packageName !in HIDDEN_PACKAGES &&
                        activity.componentName.packageName != context.packageName
                }.map { activity ->
                    activity.toItem(uxr, carPackageManager, recentPackages, mirroring)
                }.toList()

        private fun restrictedItems(
            setting: String,
            separator: String,
            flags: Int,
            type: AppGridItemType,
            uxr: UxrState,
            carPackageManager: CarPackageManager?,
            recentPackages: Set<String>,
            mirroring: MirroringSession,
            knownPackages: Set<String>,
        ): List<AppGridItem> =
            restrictedActivities(setting, separator, flags)
                .filterNot { it.activityInfo.packageName in knownPackages }
                .map { info ->
                    info.toItem(
                        type = type,
                        uxr = uxr,
                        carPackageManager = carPackageManager,
                        recentPackages = recentPackages,
                        mirroring = mirroring,
                    )
                }

        private fun LauncherActivityInfo.toItem(
            uxr: UxrState,
            carPackageManager: CarPackageManager?,
            recentPackages: Set<String>,
            mirroring: MirroringSession,
        ): AppGridItem =
            createItem(
                component = componentName,
                label = label.toString(),
                type = AppGridItemType.ACTIVITY,
                uxr = uxr,
                carPackageManager = carPackageManager,
                recentPackages = recentPackages,
                mirroring = mirroring,
            )

        private fun ResolveInfo.toItem(
            type: AppGridItemType,
            uxr: UxrState,
            carPackageManager: CarPackageManager?,
            recentPackages: Set<String>,
            mirroring: MirroringSession,
        ): AppGridItem {
            val component =
                when (type) {
                    AppGridItemType.MEDIA_SERVICE -> ComponentName(serviceInfo.packageName, serviceInfo.name)
                    else -> ComponentName(activityInfo.packageName, activityInfo.name)
                }
            return createItem(
                component = component,
                label = loadLabel(packageManager).toString(),
                type = type,
                uxr = uxr,
                carPackageManager = carPackageManager,
                recentPackages = recentPackages,
                mirroring = mirroring,
            )
        }

        private fun createItem(
            component: ComponentName,
            label: String,
            type: AppGridItemType,
            uxr: UxrState,
            carPackageManager: CarPackageManager?,
            recentPackages: Set<String>,
            mirroring: MirroringSession,
        ): AppGridItem {
            val isMedia = type == AppGridItemType.MEDIA_SERVICE
            val optimized = isMedia || carPackageManager.isDistractionOptimized(component)
            val availability =
                when {
                    type == AppGridItemType.TOS_RESTRICTED_ACTIVITY -> AppGridAvailability.TOS_REVIEW_REQUIRED
                    !uxr.serviceAvailable -> AppGridAvailability.CAR_SERVICE_UNAVAILABLE
                    uxr.requiresDistractionOptimization && !optimized ->
                        AppGridAvailability.REQUIRES_DISTRACTION_OPTIMIZATION
                    else -> AppGridAvailability.AVAILABLE
                }
            return AppGridItem(
                component = LauncherComponent(component.packageName, component.className, userId),
                label = label,
                type = type,
                isRecent = component.packageName in recentPackages,
                isDistractionOptimized = optimized,
                availability = availability,
                mirroringRedirectUri =
                    mirroring.redirectIntentUri.takeIf { mirroring.packageName == component.packageName },
            )
        }

        private fun CarPackageManager?.isDistractionOptimized(component: ComponentName): Boolean =
            this?.let { manager ->
                runCatching {
                    manager.isActivityDistractionOptimized(component.packageName, component.className)
                }.getOrDefault(false)
            } == true

        private fun mediaServices(): List<ResolveInfo> =
            packageManager
                .queryIntentServices(
                    Intent(MediaBrowserService.SERVICE_INTERFACE),
                    PackageManager.ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER.toLong()),
                ).filter { info ->
                    info.serviceInfo?.let { service ->
                        service.enabled &&
                            service.exported &&
                            service.packageName !in HIDDEN_PACKAGES &&
                            isMediaTemplate(ComponentName(service.packageName, service.name))
                    } == true
                }.distinctBy { it.serviceInfo.name }

        /** Resolve the stock CarMediaApp classifier without adding its implementation to the APK. */
        private fun isMediaTemplate(component: ComponentName): Boolean =
            runCatching {
                mediaTemplateMethod?.invoke(null, context, component) as? Boolean
            }.getOrNull() ?: false

        private fun restrictedActivities(
            setting: String,
            separator: String,
            flags: Int,
        ): List<ResolveInfo> {
            val packages =
                Settings.Secure
                    .getString(context.contentResolver, setting)
                    .orEmpty()
                    .split(separator)
                    .filter(String::isNotBlank)
                    .toSet()
            if (packages.isEmpty()) return emptyList()
            return packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    PackageManager.ResolveInfoFlags.of((PackageManager.GET_RESOLVED_FILTER or flags).toLong()),
                ).filter { it.activityInfo?.packageName in packages }
        }

        private fun readTosState(): TosState {
            val value = Settings.Secure.getString(context.contentResolver, CarSettings.Secure.KEY_USER_TOS_ACCEPTED)
            return TosState(accepted = value == TOS_ACCEPTED, blocksApps = value == TOS_NOT_ACCEPTED)
        }

        private fun shouldShowTosBanner(): Boolean {
            if (!resourceBoolean("config_enable_tos_banner", default = true)) {
                return false
            }
            val dismissal =
                legacyPreferences().getLong(TOS_BANNER_DISMISS_TIME_KEY, 0)
            val intervalDays = resourceInteger("config_tos_banner_resurface_time_days", default = 1)
            return if (intervalDays == 0) {
                dismissal < bootEpochSeconds()
            } else {
                Instant.now().epochSecond - dismissal > TimeUnit.DAYS.toSeconds(intervalDays.toLong())
            }
        }

        private fun bootEpochSeconds(): Long {
            val now = Instant.now().epochSecond
            val elapsed = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime())
            return now - elapsed
        }

        private fun appGridOrientation(): AppGridOrientation =
            if (resourceBoolean("use_vertical_app_grid", default = false)) {
                AppGridOrientation.VERTICAL
            } else {
                AppGridOrientation.HORIZONTAL
            }

        private fun resourceBoolean(
            name: String,
            default: Boolean,
        ): Boolean {
            val identifier = context.resources.getIdentifier(name, "bool", context.packageName)
            return identifier.takeIf { it != 0 }?.let(context.resources::getBoolean) ?: default
        }

        private fun resourceInteger(
            name: String,
            default: Int,
        ): Int {
            val identifier = context.resources.getIdentifier(name, "integer", context.packageName)
            return identifier.takeIf { it != 0 }?.let(context.resources::getInteger) ?: default
        }

        private fun launchAvailable(
            item: AppGridItem,
            display: DisplayTarget,
            mode: AppGridMode,
        ) {
            val component = ComponentName(item.component.packageName, item.component.className)
            item.mirroringRedirectUri?.let { redirectUri ->
                context.startActivity(
                    Intent
                        .parseUri(redirectUri, Intent.URI_INTENT_SCHEME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    optionsFor(display),
                )
                return
            }
            when (item.type) {
                AppGridItemType.ACTIVITY -> launcherApps.startMainActivity(component, user, null, optionsFor(display))
                AppGridItemType.MEDIA_SERVICE -> launchMedia(component, display, mode)
                AppGridItemType.DISABLED_ACTIVITY -> {
                    packageManager.setApplicationEnabledSetting(
                        component.packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0,
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .setComponent(component)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        optionsFor(display),
                    )
                }

                AppGridItemType.TOS_RESTRICTED_ACTIVITY -> startTosActivity(display)
            }
        }

        private fun startTosActivity(display: DisplayTarget) {
            context.startActivity(
                Intent(ACTION_SHOW_USER_TOS)
                    .putExtra(EXTRA_TOS_SHOW_VALUE_PROP, false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                optionsFor(display),
            )
        }

        @SuppressLint("MissingPermission")
        private fun launchMedia(
            component: ComponentName,
            display: DisplayTarget,
            mode: AppGridMode,
        ) {
            if (mode.opensMediaCenter) {
                context.startActivity(
                    Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
                        .putExtra(CarMediaIntents.EXTRA_MEDIA_COMPONENT, component.flattenToString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    optionsFor(display),
                )
            } else {
                val manager =
                    carConnection.car.value?.let { car ->
                        runCatching { car.getCarManager(CarMediaManager::class.java) }.getOrNull()
                    } ?: error("Car media service is unavailable.")
                manager.setMediaSource(component, CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)
            }
        }

        private fun optionsFor(display: DisplayTarget): android.os.Bundle =
            ActivityOptions
                .makeBasic()
                .apply { setLaunchDisplayId(display.displayId) }
                .toBundle()

        /** AndroidX PreferenceManager's canonical default-file name, kept for stock TOS migration. */
        private fun legacyPreferences() =
            context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE,
            )

        @SuppressLint("MissingPermission")
        private fun recentPackages(): Set<String> {
            val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptySet()
            val now = System.currentTimeMillis()
            return runCatching {
                manager
                    .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - RECENT_WINDOW_MS, now)
                    .orEmpty()
                    .asSequence()
                    .filter { it.lastTimeUsed > 0L }
                    .sortedByDescending { it.lastTimeUsed }
                    .take(MAX_RECENT_PACKAGES)
                    .map { it.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
        }

        private fun isTelephonyAppForPassenger(item: AppGridItem): Boolean {
            val userManager = context.getSystemService(UserManager::class.java)
            val isVisibleBackgroundUser = userManager?.isVisibleBackgroundUserCompat() == true
            if (!isVisibleBackgroundUser) return false
            return runCatching {
                packageManager
                    .getPackageInfo(
                        item.component.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                    ).requestedPermissions
                    ?.contains(android.Manifest.permission.MANAGE_OWN_CALLS) == true
            }.getOrDefault(false)
        }

        private fun UserManager.isVisibleBackgroundUserCompat(): Boolean =
            runCatching {
                val type = UserManager::class.java
                val foreground = type.getMethod("isUserForeground").invoke(this) as Boolean
                val visible = type.getMethod("isUserVisible").invoke(this) as Boolean
                val profile = type.getMethod("isProfile").invoke(this) as Boolean
                return@runCatching !foreground && visible && !profile
            }.getOrDefault(false)

        private fun secureSettingChanges(): Flow<Unit> =
            callbackFlow {
                val observer =
                    object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                SECURE_SETTINGS.forEach { setting ->
                    context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(setting), false, observer)
                }
                trySend(Unit)
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }.conflate()

        private fun initialState() =
            AppGridState(
                items = emptyList(),
                orientation = appGridOrientation(),
                restriction = DrivingRestriction.FULLY_RESTRICTED,
                tosAccepted = false,
                shouldShowTosBanner = false,
                canReorder = false,
            )

        private data class TosState(
            val accepted: Boolean,
            val blocksApps: Boolean,
        )

        private companion object {
            const val TAG = "CarLauncher.AppGrid"
            const val PER_USER_RANGE = 100_000
            const val TOS_ACCEPTED = "2"
            const val TOS_NOT_ACCEPTED = "1"
            const val RESOURCE_OVERUSE_SEPARATOR = ";"
            const val TOS_SEPARATOR = ","
            const val TOS_BANNER_DISMISS_TIME_KEY = "TOS_BANNER_DISMISS_TIME"
            const val ACTION_SHOW_USER_TOS = "com.android.car.SHOW_USER_TOS_ACTIVITY"
            const val EXTRA_TOS_SHOW_VALUE_PROP = "show_value_prop"
            const val MEDIA_PACKAGE = "com.android.car.media"
            const val MEDIA_SOURCE_CLASS = "com.android.car.media.common.source.MediaSource"
            const val RECENT_WINDOW_MS = 7 * 24 * 60 * 60 * 1_000L
            const val MAX_RECENT_PACKAGES = 6
            val HIDDEN_PACKAGES = setOf("com.android.permissioncontroller", "com.android.systemui")
            val SECURE_SETTINGS =
                listOf(
                    CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                    CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
                    CarSettings.Secure.KEY_USER_TOS_ACCEPTED,
                )
        }
    }
