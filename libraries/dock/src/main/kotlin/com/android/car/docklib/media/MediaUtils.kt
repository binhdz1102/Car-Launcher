package com.android.car.docklib.media

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.media.MediaBrowserService

/** Media task/session helpers shared by Dock hosts. */
object MediaUtils {
    @JvmField
    val CAR_MEDIA_ACTIVITY = ComponentName("com.android.car.media", "com.android.car.media.MediaActivity")

    const val CAR_MEDIA_DATA_SCHEME = "custom"
    const val ACTION_MEDIA_TEMPLATE = "android.car.media.action.MEDIA_TEMPLATE"
    const val EXTRA_MEDIA_COMPONENT = "android.car.media.extra.MEDIA_COMPONENT"

    @JvmStatic
    fun getMediaComponentName(taskInfo: ActivityManager.RunningTaskInfo): ComponentName? =
        taskInfo.baseIntent.data
            ?.takeIf { it.scheme == CAR_MEDIA_DATA_SCHEME }
            ?.let { data -> ComponentName.unflattenFromString(data.schemeSpecificPart.removePrefix("/")) }

    @JvmStatic
    fun isMediaComponent(component: ComponentName?): Boolean = component == CAR_MEDIA_ACTIVITY

    @JvmStatic
    fun createLaunchIntent(componentName: ComponentName): Intent =
        Intent(ACTION_MEDIA_TEMPLATE)
            .putExtra(EXTRA_MEDIA_COMPONENT, componentName.flattenToString())

    @JvmStatic
    fun fetchMediaServiceComponents(
        packageManager: PackageManager,
        packageName: String? = null,
    ): MutableSet<ComponentName> {
        val intent =
            Intent(MediaBrowserService.SERVICE_INTERFACE).apply {
                packageName?.let(::setPackage)
            }
        return packageManager
            .queryIntentServices(intent, 0)
            .mapNotNull { info ->
                info.serviceInfo?.let { service ->
                    ComponentName(service.packageName, service.name)
                }
            }.toMutableSet()
    }
}
