package com.android.car.carlauncher.homescreen

import android.car.settings.CarSettings
import android.database.ContentObserver
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Placeholder map surface shown while the current user's map terms of service are pending.
 *
 * The stock activity is intentionally a green, otherwise empty surface. The review affordance
 * is only made visible when CarSettings reports at least one application blocked by unaccepted
 * terms. A content-observer adapter feeds this lifecycle-owned Flow so settings changes are
 * reflected without a polling Handler or a stale activity state.
 */
class MapTosActivity : AppCompatActivity() {
    private lateinit var reviewButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_fallback)
        applySystemBarInsets()
        reviewButton = findViewById(R.id.map_tos_review_button)
        reviewButton.setOnClickListener {
            startActivity(
                Intent(ACTION_SHOW_USER_TOS)
                    .putExtra(EXTRA_SHOW_VALUE_PROP, false),
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tosReviewRequired.collect { required ->
                    reviewButton.visibility = if (required) TextView.VISIBLE else TextView.GONE
                    if (required) {
                        reviewButton.setText(R.string.map_tos_review_button_text)
                    }
                }
            }
        }
    }

    private val tosReviewRequired = callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(readTosReviewRequired())
                }
            }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS),
            false,
            observer,
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(CarSettings.Secure.KEY_USER_TOS_ACCEPTED),
            false,
            observer,
        )
        trySend(readTosReviewRequired())
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    private fun readTosReviewRequired(): Boolean =
        Settings.Secure
            .getString(
                contentResolver,
                CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
            )
            .orEmpty()
            .split(TOS_SEPARATOR)
            .any(String::isNotBlank)

    private companion object {
        const val ACTION_SHOW_USER_TOS = "com.android.car.SHOW_USER_TOS_ACTIVITY"
        const val EXTRA_SHOW_VALUE_PROP = "show_value_prop"
        const val TOS_SEPARATOR = ","
    }
}
