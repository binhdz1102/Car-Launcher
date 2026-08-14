package com.android.car.carlauncher

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import timber.log.Timber

/** Trusted overlay home-card surface; widget hosting belongs exclusively to [WidgetHostActivity]. */
@Suppress("SoonBlockedPrivateApi") // Platform-signed launcher requires the trusted-overlay flag.
class ControlBarActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.control_bar_container)
        applySystemBarInsets()
        runCatching {
            Window::class.java
                .getDeclaredMethod("addPrivateFlags", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(window, PRIVATE_FLAG_TRUSTED_OVERLAY)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Trusted control-bar overlay is unavailable")
        }
    }

    private companion object {
        const val TAG = "CarLauncher.ControlBarActivity"
        const val PRIVATE_FLAG_TRUSTED_OVERLAY = 0x20000000
    }
}
