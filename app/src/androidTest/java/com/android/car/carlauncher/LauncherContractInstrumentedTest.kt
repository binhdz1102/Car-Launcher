package com.android.car.carlauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the package contracts consumed by Automotive framework and CarSystemUI. */
@RunWith(AndroidJUnit4::class)
class LauncherContractInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun packageIdentityAndCriticalPrivilegedPermissionsArePresent() {
        assertEquals(PACKAGE_NAME, context.packageName)

        CRITICAL_PERMISSIONS.forEach { permission ->
            assertEquals(
                "$permission must be granted to the replacement launcher",
                PackageManager.PERMISSION_GRANTED,
                packageManager.checkPermission(permission, PACKAGE_NAME),
            )
        }
    }

    @Test
    fun homeAndAppGridActionsResolveToReplacementComponents() {
        val homeActivities =
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(PACKAGE_NAME),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        assertTrue(
            homeActivities.any { it.activityInfo.name == "$PACKAGE_NAME.CarLauncher" },
        )

        val appGridActivities =
            packageManager.queryIntentActivities(
                Intent(ACTION_APP_GRID).setPackage(PACKAGE_NAME),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        assertTrue(
            appGridActivities.any {
                it.activityInfo.name ==
                    "$PACKAGE_NAME.feature.launcher.presentation.AppGridActivity"
            },
        )
    }

    @Test
    fun explicitCarSystemUiActivitiesExistAndAreDistractionOptimized() {
        DISTRACTION_OPTIMIZED_ACTIVITIES.forEach { className ->
            val info =
                packageManager.getActivityInfo(
                    ComponentName(PACKAGE_NAME, className),
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            assertTrue("$className must be enabled", info.enabled)
            assertTrue("$className must be exported", info.exported)
            assertTrue(
                "$className must declare distractionOptimized=true",
                info.metaData?.getBoolean(DISTRACTION_OPTIMIZED_METADATA) == true,
            )
        }

        assertNotNull(
            packageManager.getActivityInfo(
                ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.AppGridActivity"),
                PackageManager.ComponentInfoFlags.of(0),
            ),
        )
    }

    @Test
    fun quickStepAndInCallServicesExposeFrameworkContracts() {
        val quickStep =
            packageManager.queryIntentServices(
                Intent(ACTION_QUICKSTEP_SERVICE).setPackage(PACKAGE_NAME),
                PackageManager.ResolveInfoFlags.of(0),
            )
        assertTrue(
            "QuickStep service action must resolve to CarQuickStepService",
            quickStep.any { it.serviceInfo.name == "$PACKAGE_NAME.recents.CarQuickStepService" },
        )

        val inCall =
            packageManager.queryIntentServices(
                Intent("android.telecom.InCallService").setPackage(PACKAGE_NAME),
                PackageManager.ResolveInfoFlags.of(0),
            )
        assertTrue(
            "InCallService action must resolve to the launcher bridge",
            inCall.any {
                it.serviceInfo.name ==
                    "$PACKAGE_NAME.homescreen.audio.telecom.InCallServiceImpl"
            },
        )
    }

    @Test
    fun calmModeProviderAndWidgetProviderArePublished() {
        val calmModeProvider =
            packageManager.resolveContentProvider(
                CALM_MODE_AUTHORITY,
                PackageManager.ComponentInfoFlags.of(0),
            )
        assertNotNull(calmModeProvider)
        assertEquals(PACKAGE_NAME, calmModeProvider?.packageName)
        assertTrue(calmModeProvider?.exported == true)

        val receiver =
            packageManager.getReceiverInfo(
                ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.DateAppWidgetProvider"),
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        assertTrue(receiver.enabled)
        assertNotNull(receiver.metaData?.getInt("android.appwidget.provider"))
    }

    @Test
    fun resetLauncherSettingsEntryIsPublished() {
        val info =
            packageManager.getActivityInfo(
                ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.ResetLauncherActivity"),
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        assertTrue(info.enabled)
        assertTrue(info.exported)
        assertTrue(info.metaData?.getInt(SETTINGS_TITLE_METADATA) != 0)
        assertEquals(SETTINGS_APPS_CATEGORY, info.metaData?.getString(SETTINGS_CATEGORY_METADATA))

        val settingsEntries =
            packageManager.queryIntentActivities(
                Intent(ACTION_EXTRA_SETTINGS).setPackage(PACKAGE_NAME),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        assertTrue(
            settingsEntries.any { it.activityInfo.name == "$PACKAGE_NAME.ResetLauncherActivity" },
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.android.car.carlauncher"
        const val ACTION_APP_GRID = "com.android.car.carlauncher.ACTION_APP_GRID"
        const val ACTION_QUICKSTEP_SERVICE = "android.intent.action.QUICKSTEP_SERVICE"
        const val CALM_MODE_AUTHORITY = "com.android.car.carlauncher.calmmode"
        const val DISTRACTION_OPTIMIZED_METADATA = "distractionOptimized"
        const val ACTION_EXTRA_SETTINGS = "com.android.settings.action.EXTRA_SETTINGS"
        const val SETTINGS_TITLE_METADATA = "com.android.settings.title"
        const val SETTINGS_CATEGORY_METADATA = "com.android.settings.category"
        const val SETTINGS_APPS_CATEGORY = "com.android.settings.category.ia.apps"

        val CRITICAL_PERMISSIONS =
            listOf(
                "android.permission.MANAGE_ACTIVITY_TASKS",
                "android.permission.START_TASKS_FROM_RECENTS",
                "android.permission.READ_FRAME_BUFFER",
                "android.permission.BIND_APPWIDGET",
            )

        val DISTRACTION_OPTIMIZED_ACTIVITIES =
            listOf(
                "$PACKAGE_NAME.CarLauncher",
                "$PACKAGE_NAME.recents.CarRecentsActivity",
                "$PACKAGE_NAME.ControlBarActivity",
                "$PACKAGE_NAME.WidgetHostActivity",
                "$PACKAGE_NAME.calmmode.CalmModeActivity",
                "$PACKAGE_NAME.homescreen.MapTosActivity",
            )
    }
}
