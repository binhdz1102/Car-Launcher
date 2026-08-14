package com.android.car.carlauncher.homescreen

import android.car.settings.CarSettings
import android.content.Intent
import android.database.ContentObserver
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
import com.android.car.carlauncher.core.platform.DrivingRestrictionMonitor
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Placeholder map surface shown while the current user's map terms of service are pending.
 *
 * The stock activity is intentionally a green, otherwise empty surface. The review affordance
 * is enabled only when the current user has not accepted the terms. A content-observer adapter
 * feeds this lifecycle-owned Flow so settings changes are reflected without polling.
 */
@Suppress("TooManyFunctions")
@AndroidEntryPoint
class MapTosActivity : AppCompatActivity() {
    @Inject lateinit var drivingRestrictionMonitor: DrivingRestrictionMonitor

    private lateinit var reviewButton: TextView
    private var tosPending = false
    private var requiresDistractionOptimization = false

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
                launch {
                    tosReviewRequired.collect { pending ->
                        tosPending = pending
                        renderReviewButton()
                    }
                }
                launch {
                    drivingRestrictionMonitor.restrictions.collect { restrictions ->
                        requiresDistractionOptimization = restrictions.requiresDistractionOptimization
                        renderReviewButton()
                    }
                }
            }
        }
    }

    private fun renderReviewButton() {
        if (!tosPending) {
            if (!isFinishing) finish()
            return
        }
        reviewButton.visibility = TextView.VISIBLE
        reviewButton.isEnabled = !requiresDistractionOptimization
        reviewButton.setText(
            if (requiresDistractionOptimization) {
                R.string.map_tos_review_button_distraction_optimized_text
            } else {
                R.string.map_tos_review_button_text
            },
        )
    }

    private val tosReviewRequired =
        callbackFlow {
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
        Settings.Secure.getString(contentResolver, CarSettings.Secure.KEY_USER_TOS_ACCEPTED) ==
            TOS_NOT_ACCEPTED

    private companion object {
        const val ACTION_SHOW_USER_TOS = "com.android.car.SHOW_USER_TOS_ACTIVITY"
        const val EXTRA_SHOW_VALUE_PROP = "show_value_prop"
        const val TOS_NOT_ACCEPTED = "1"
    }
}
