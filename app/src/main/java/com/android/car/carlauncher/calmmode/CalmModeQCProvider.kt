package com.android.car.carlauncher.calmmode

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.android.car.carlauncher.R
import com.android.car.qc.QCItem
import com.android.car.qc.QCList
import com.android.car.qc.QCRow
import com.android.car.qc.provider.BaseQCProvider
import java.util.concurrent.atomic.AtomicInteger

/** Remote Quick Control provider for Calm Mode, matching the AOSP URI and allowlist contract. */
class CalmModeQCProvider : BaseQCProvider() {
    private var qcItem: QCItem? = null
    private val pendingIntentRequestCode = AtomicInteger(0)

    override fun onCreate(): Boolean {
        if (!isCalmModeEnabled()) return false
        val created = super.onCreate()
        qcItem = createQcItem()
        return created
    }

    protected override fun onBind(uri: Uri): QCItem? {
        if (!isCalmModeEnabled()) return null
        if (removeParameterFromUri(uri) != CALM_MODE_URI) {
            throw IllegalArgumentException("No QCItem found for uri: $uri")
        }
        return qcItem ?: createQcItem().also { qcItem = it }
    }

    protected override fun getAllowlistedPackages(): Set<String> =
        getContext()
            ?.resources
            ?.getStringArray(R.array.launcher_qc_provider_package_allowlist)
            ?.toSet()
            .orEmpty()

    private fun createQcItem(): QCItem {
        val context = requireNotNull(getContext())
        val component =
            context.resources
                .getString(R.string.config_calmMode_componentName)
                .let(ComponentNameParser::parse)
        val intent = Intent().setComponent(component)
        val options =
            ActivityOptions
                .makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
        val action =
            PendingIntent.getActivity(
                context,
                pendingIntentRequestCode.getAndIncrement(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                options.toBundle(),
            )
        val row =
            QCRow
                .Builder()
                .setTitle(context.getString(R.string.calm_mode_title))
                .setPrimaryAction(action)
                .build()
        return QCList.Builder().addRow(row).build()
    }

    private fun isCalmModeEnabled(): Boolean = getContext()?.resources?.getBoolean(R.bool.config_enableCalmMode) == true

    private object ComponentNameParser {
        fun parse(value: String): android.content.ComponentName =
            android.content.ComponentName.unflattenFromString(value)
                ?: error("Invalid Calm Mode component: $value")
    }

    companion object {
        const val AUTHORITY = "com.android.car.carlauncher.calmmode"
        private const val CALM_MODE_SEGMENT = "calm_mode"

        @JvmField
        val CALM_MODE_URI: Uri =
            Uri
                .Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(CALM_MODE_SEGMENT)
                .build()

        @JvmStatic
        fun removeParameterFromUri(uri: Uri?): Uri? = uri?.buildUpon()?.clearQuery()?.build()
    }
}
