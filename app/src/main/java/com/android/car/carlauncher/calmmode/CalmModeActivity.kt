package com.android.car.carlauncher.calmmode

import android.os.Bundle
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.calmmode.presentation.CalmModeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Low-distraction persistent activity used by the scalable SystemUI calm-mode panel. */
@AndroidEntryPoint
class CalmModeActivity : AppCompatActivity() {
    private val viewModel: CalmModeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calm_mode)
        applySystemBarInsets()
        findViewById<View>(R.id.calm_mode_container).setOnClickListener { finish() }
        findViewById<TextClock>(R.id.calm_clock).format24Hour = "HH:mm"
        findViewById<TextClock>(R.id.calm_clock).format12Hour = "h:mm"
        viewModel.onStarted()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val title =
                        state.playback.title
                            .takeIf(String::isNotBlank)
                            ?.let { mediaTitle ->
                                state.playback.artist
                                    .takeIf(String::isNotBlank)
                                    ?.let { artist -> "$mediaTitle  •  $artist" }
                                    ?: mediaTitle
                            }
                    findViewById<TextView>(R.id.calm_media_title).apply {
                        text = title.orEmpty()
                        visibility = if (title == null) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onStop() {
        viewModel.onStopped()
        super.onStop()
    }
}
