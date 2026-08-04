package com.android.car.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.car.carlauncher.ui.theme.CarLauncherTheme
import timber.log.Timber

/**
 * Small Gradle-built HOME application used for rapid development on the AAOS
 * emulator. The production AOSP CarLauncher remains a Soong application and
 * is intentionally not copied into this standalone project.
 */
class CarLauncher : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")
        setContent {
            CarLauncherTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.launcher_status))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.d("onStart()")
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("onRestart()")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume()")
    }

    override fun onPause() {
        Timber.d("onPause()")
        super.onPause()
    }

    override fun onStop() {
        Timber.d("onStop()")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.d("onDestroy()")
        super.onDestroy()
    }
}
