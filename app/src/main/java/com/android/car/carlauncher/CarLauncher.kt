package com.android.car.carlauncher

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
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
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.launcher_container, LauncherFragment(), launcherFragmentTag)
                .commitNow()
        }
        Timber.tag(TAG).i("CarLauncher created for uid=%d", Process.myUid())
        fragment()?.handleHostIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fragment()?.handleHostIntent(intent)
        Timber.tag(TAG).d("Received new launcher intent: %s", intent.action)
    }

    private fun fragment(): LauncherFragment? =
        supportFragmentManager.findFragmentByTag(launcherFragmentTag) as? LauncherFragment

    private companion object {
        const val TAG = "CarLauncher"
    }
}
