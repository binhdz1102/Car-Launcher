package com.android.car.carlauncher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.appgrid.domain.AppGridMode
import com.android.car.carlauncher.feature.appgrid.presentation.AppGridFragment
import dagger.hilt.android.AndroidEntryPoint

/** Stable exported AAOS component; app-grid implementation is isolated in feature:appgrid. */
@AndroidEntryPoint
class AppGridActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_grid_host)
        applySystemBarInsets()
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, AppGridFragment.newInstance(modeFrom(intent)))
                .commitNow()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? AppGridFragment)
            ?.updateMode(modeFrom(intent))
    }

    private fun modeFrom(intent: Intent?): AppGridMode =
        AppGridMode.fromIntentValue(
            intent?.getStringExtra(AppGridMode.INTENT_EXTRA),
        )
}
