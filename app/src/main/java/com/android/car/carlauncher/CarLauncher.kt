package com.android.car.carlauncher

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.view.Display
import androidx.appcompat.app.AppCompatActivity
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.launcher.presentation.LauncherFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/** HOME entry point. Feature state and TaskView boundaries live in launcher:presentation. */
@AndroidEntryPoint
class CarLauncher : AppCompatActivity() {
    private val launcherFragmentTag = "launcher-root"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher_host)
        applySystemBarInsets()
        if (isPassengerDisplay()) {
            launchPassengerAppGrid()
            return
        }
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.launcher_container, LauncherFragment(), launcherFragmentTag)
                .commitNow()
        }
        Timber.tag(TAG).i("CarLauncher created for uid=%d", Process.myUid())
        fragment()?.handleHostIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isPassengerDisplay()) {
            launchPassengerAppGrid()
            return
        }
        fragment()?.handleHostIntent(intent)
        Timber.tag(TAG).d("Received new launcher intent: %s", intent.action)
    }

    private fun fragment(): LauncherFragment? {
        val fragment = supportFragmentManager.findFragmentByTag(launcherFragmentTag)
        return fragment as? LauncherFragment
    }

    private fun isPassengerDisplay(): Boolean {
        val isSecondaryDisplay = currentDisplayId() != Display.DEFAULT_DISPLAY
        return isSecondaryDisplay || supportsVisibleBackgroundUsers()
    }

    private fun currentDisplayId(): Int = display?.displayId ?: Display.DEFAULT_DISPLAY

    private fun supportsVisibleBackgroundUsers(): Boolean =
        runCatching {
            UserManager::class.java
                .getMethod("isVisibleBackgroundUsersOnDefaultDisplaySupported")
                .invoke(getSystemService(UserManager::class.java)) as Boolean
        }.getOrDefault(false)

    private fun launchPassengerAppGrid() {
        startActivity(
            Intent("com.android.car.carlauncher.ACTION_APP_GRID")
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Timber.tag(TAG).i("Passenger display=%d redirected to App Grid", currentDisplayId())
    }

    private companion object {
        const val TAG = "CarLauncher"
    }
}
