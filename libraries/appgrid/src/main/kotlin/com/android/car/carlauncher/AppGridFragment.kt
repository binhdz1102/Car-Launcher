package com.android.car.carlauncher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.android.car.carlauncher.appgridlib.R

private const val APP_TYPE_LAUNCHABLES_VALUE = 1
private const val APP_TYPE_MEDIA_SERVICES_VALUE = 2

/** Compatibility host for clients that still embed the stock AppGrid library fragment. */
class AppGridFragment : Fragment() {
    private var mode: Mode = Mode.ALL_APPS

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View = inflater.inflate(R.layout.app_grid_fragment, container, false)

    fun updateMode(mode: Mode) {
        this.mode = mode
        requireActivity().title = getString(mode.titleStringId)
    }

    enum class Mode(
        @field:StringRes @param:StringRes val titleStringId: Int,
        @field:AppTypes @param:AppTypes val appTypes: Int,
        val openMediaCenter: Boolean,
    ) {
        ALL_APPS(
            R.string.app_launcher_title_all_apps,
            APP_TYPE_LAUNCHABLES_VALUE or APP_TYPE_MEDIA_SERVICES_VALUE,
            true,
        ),
        MEDIA_ONLY(R.string.app_launcher_title_media_only, APP_TYPE_MEDIA_SERVICES_VALUE, true),
        MEDIA_POPUP(R.string.app_launcher_title_media_only, APP_TYPE_MEDIA_SERVICES_VALUE, false),
        ;

        companion object {
            const val APP_TYPE_LAUNCHABLES = APP_TYPE_LAUNCHABLES_VALUE
            const val APP_TYPE_MEDIA_SERVICES = APP_TYPE_MEDIA_SERVICES_VALUE
        }
    }

    annotation class AppTypes {
        companion object {
            const val APP_TYPE_LAUNCHABLES = APP_TYPE_LAUNCHABLES_VALUE
            const val APP_TYPE_MEDIA_SERVICES = APP_TYPE_MEDIA_SERVICES_VALUE
        }
    }

    companion object {
        const val TAG = "AppGridFragment"
        const val DEBUG_BUILD = false
        const val MODE_INTENT_EXTRA = "com.android.car.carlauncher.mode"

        @JvmStatic
        fun newInstance(mode: Mode): AppGridFragment =
            AppGridFragment().apply { arguments = Bundle().apply { putString(MODE_INTENT_EXTRA, mode.name) } }
    }
}
