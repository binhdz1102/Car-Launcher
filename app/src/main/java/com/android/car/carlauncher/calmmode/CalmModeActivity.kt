package com.android.car.carlauncher.calmmode

import android.os.Bundle
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.platform.LauncherFeatureFlags
import com.android.car.carlauncher.feature.calmmode.domain.CalmModePolicy
import com.android.car.carlauncher.feature.calmmode.presentation.CalmModeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Low-distraction persistent activity used by the scalable SystemUI calm-mode panel. */
@AndroidEntryPoint
class CalmModeActivity : AppCompatActivity() {
    private val viewModel: CalmModeViewModel by viewModels()

    @Inject lateinit var featureFlags: LauncherFeatureFlags

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!featureFlags.calmMode || !resources.getBoolean(R.bool.config_enableCalmMode)) {
            finish()
            return
        }
        setContentView(R.layout.activity_calm_mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(
            WindowInsetsCompat.Type.statusBars(),
        )
        findViewById<View>(R.id.calm_mode_container).setOnClickListener { finish() }
        findViewById<TextClock>(R.id.calm_clock).format24Hour = "HH:mm"
        findViewById<TextClock>(R.id.calm_clock).format12Hour = "hh:mm"
        applyConfiguredVisibility()
        animateEntry()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val title =
                        CalmModePolicy.formatMediaTitle(
                            state.playback.title,
                            state.playback.artist,
                            getString(R.string.calm_mode_separator),
                        )
                    findViewById<TextView>(R.id.calm_media_title).apply {
                        text = title.orEmpty()
                        if (resources.getBoolean(R.bool.config_calmMode_showMedia)) {
                            visibility = if (title == null) View.GONE else View.VISIBLE
                        }
                    }
                    findViewById<TextView>(R.id.calm_temperature).apply {
                        text =
                            state.temperature
                                ?.let { temperature ->
                                    CalmModePolicy.formatTemperature(
                                        temperature,
                                        resources.configuration.locales[0],
                                    )
                                }.orEmpty()
                        visibility =
                            if (resources.getBoolean(R.bool.config_calmMode_showTemperature) &&
                                text.isNotBlank()
                            ) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isFinishing) viewModel.onStarted()
    }

    override fun onStop() {
        viewModel.onStopped()
        super.onStop()
    }

    private fun applyConfiguredVisibility() {
        val visibility =
            CalmModePolicy.visibility(
                featureEnabled = featureFlags.calmMode,
                showClock = resources.getBoolean(R.bool.config_calmMode_showClock),
                showDate = resources.getBoolean(R.bool.config_calmMode_showDate),
                showMedia = resources.getBoolean(R.bool.config_calmMode_showMedia),
                showNavigation = resources.getBoolean(R.bool.config_calmMode_showNavigation),
                showTemperature = resources.getBoolean(R.bool.config_calmMode_showTemperature),
            )
        findViewById<TextClock>(R.id.calm_clock).visibility = visibility.clock.toVisibility()
        findViewById<TextClock>(R.id.calm_date).visibility = visibility.date.toVisibility()
        findViewById<TextView>(R.id.calm_media_title).visibility = visibility.media.toVisibility()
        findViewById<TextView>(R.id.calm_temperature).visibility = visibility.temperature.toVisibility()
    }

    private fun animateEntry() {
        findViewById<View>(R.id.calm_mode_container).apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(resources.getInteger(R.integer.calm_mode_activity_fade_duration).toLong())
                .start()
        }
    }

    private fun Boolean.toVisibility(): Int = if (this) View.VISIBLE else View.GONE
}
