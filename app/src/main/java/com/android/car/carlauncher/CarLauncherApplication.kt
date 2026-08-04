package com.android.car.carlauncher

import android.app.Application
import com.android.car.carlauncher.logging.AppTimberTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CarLauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(
            AppTimberTree(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                gitCommitHash = BuildConfig.GIT_COMMIT_HASH,
            ),
        )
    }
}
