package com.android.car.carlauncher.feature.home.data

import android.car.settings.CarSettings
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.android.car.carlauncher.core.platform.LauncherFeatureFlags
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTargetType
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskTarget
import com.android.car.carlauncher.feature.home.domain.NavigationTargetInvalidation
import com.android.car.carlauncher.feature.home.domain.NavigationTargetResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AAOS map resolver matching the AOSP [Intent.CATEGORY_APP_MAPS] contract.
 *
 * App Grid safety rules intentionally do not apply here: the platform's embedded map surface is
 * resolved by the map/default/TOS policy, then CarSystemUI decides whether it may be shown.
 */
@Singleton
class AndroidNavigationTargetResolver
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val featureFlags: LauncherFeatureFlags,
    ) : NavigationTargetResolver,
        NavigationTargetInvalidation {
        private val packageManager = context.packageManager

        override val changes: Flow<Unit> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                val contentResolver = settingsContext().contentResolver
                contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS),
                    false,
                    observer,
                )
                contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(CarSettings.Secure.KEY_USER_TOS_ACCEPTED),
                    false,
                    observer,
                )
                trySend(Unit)
                awaitClose { contentResolver.unregisterContentObserver(observer) }
            }

        override suspend fun resolve(): Result<HomeEmbeddedTaskTarget> =
            runCatching {
                val intent = resolveMapIntent()
                val component =
                    intent.component
                        ?: intent.resolveActivity(packageManager)
                        ?: error("No navigation activity is installed for the current vehicle user.")
                val resolved = packageManager.resolveActivity(intent, 0)
                HomeEmbeddedTaskTarget(
                    componentName = component.flattenToString(),
                    label = resolved?.loadLabel(packageManager)?.toString() ?: "Navigation",
                    type = HomeEmbeddedTargetType.NAVIGATION,
                    launchIntentUri = intent.toUri(Intent.URI_INTENT_SCHEME),
                )
            }.onFailure { throwable ->
                Timber.tag(TAG).w(throwable, "Unable to resolve the AOSP maps intent")
            }

        private fun resolveMapIntent(): Intent {
            val defaultIntent =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_MAPS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            val defaultComponent = defaultIntent.resolveActivity(packageManager)
            defaultIntent.component = defaultComponent

            val preferred =
                preferredMapIntents().firstOrNull { candidate ->
                    (defaultComponent == null || defaultComponent.packageName == candidate.`package`) &&
                        packageManager.resolveActivity(candidate, 0) != null
                }
            val selected = smallCanvasMapIntent() ?: preferred ?: defaultIntent
            return maybeReplaceWithTosIntent(selected)
        }

        private fun smallCanvasMapIntent(): Intent? =
            stringResource("config_smallCanvasOptimizedMapIntent")
                .takeIf(String::isNotBlank)
                ?.let { serialized ->
                    runCatching {
                        Intent.parseUri(serialized, Intent.URI_INTENT_SCHEME).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                        )
                    }.onFailure {
                        Timber.tag(TAG).w(it, "Invalid small-canvas map intent")
                    }.getOrNull()
                }

        private fun preferredMapIntents(): List<Intent> =
            stringArrayResource("config_homeCardPreferredMapActivities")
                .mapNotNull { serialized ->
                    runCatching {
                        Intent.parseUri(serialized, Intent.URI_ANDROID_APP_SCHEME)
                    }.onFailure {
                        Timber.tag(TAG).w(it, "Invalid preferred map intent: %s", serialized)
                    }.getOrNull()
                }

        private fun maybeReplaceWithTosIntent(mapIntent: Intent): Intent {
            val mapPackage = mapIntent.component?.packageName
            return when {
                !featureFlags.tosRestrictionsEnabled -> mapIntent
                !isTosAccepted() && (mapPackage == null || mapPackage in tosDisabledPackages()) ->
                    tosMapIntent() ?: mapIntent
                else -> mapIntent
            }
        }

        private fun tosMapIntent(): Intent? =
            stringResource("config_tosMapIntent")
                .takeIf(String::isNotBlank)
                ?.let { serialized ->
                    runCatching {
                        Intent
                            .parseUri(serialized, Intent.URI_ANDROID_APP_SCHEME)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                            )
                    }.onFailure {
                        Timber.tag(TAG).w(it, "Invalid TOS map intent")
                    }.getOrNull()
                }

        private fun isTosAccepted(): Boolean =
            Settings.Secure.getString(
                settingsContext().contentResolver,
                CarSettings.Secure.KEY_USER_TOS_ACCEPTED,
            ) != TOS_NOT_ACCEPTED

        private fun tosDisabledPackages(): Set<String> =
            Settings.Secure
                .getString(
                    settingsContext().contentResolver,
                    CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
                )?.split(TOS_DISABLED_APPS_SEPARATOR)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty()

        // The application context is already scoped to the foreground vehicle user. Keeping the
        // access here avoids a hidden Context API while preserving the AOSP per-user lookup.
        private fun settingsContext(): Context = context

        private fun stringResource(name: String): String {
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            return id.takeIf { it != 0 }?.let(context::getString).orEmpty()
        }

        private fun stringArrayResource(name: String): Array<String> {
            val id = context.resources.getIdentifier(name, "array", context.packageName)
            return if (id == 0) {
                emptyArray()
            } else {
                context.resources
                    .getStringArray(id)
                    .toList()
                    .toTypedArray()
            }
        }

        private companion object {
            const val TAG = "CarLauncher.Home.NavigationResolver"
            const val TOS_NOT_ACCEPTED = "1"
            const val TOS_DISABLED_APPS_SEPARATOR = ","
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeNavigationDataModule {
    @Binds
    abstract fun bindNavigationTargetResolver(implementation: AndroidNavigationTargetResolver): NavigationTargetResolver

    @Binds
    abstract fun bindInvalidation(implementation: AndroidNavigationTargetResolver): NavigationTargetInvalidation
}
