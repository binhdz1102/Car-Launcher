package com.android.car.carlauncher.calmmode

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.launcher.presentation.CalmModeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.android.car.carlauncher.feature.launcher.presentation.R as LauncherR

/** Low-distraction persistent activity used by the scalable SystemUI calm-mode panel. */
@AndroidEntryPoint
class CalmModeActivity : AppCompatActivity() {
    private val viewModel: CalmModeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calm_mode)
        applySystemBarInsets()
        findViewById<View>(R.id.calm_close).setOnClickListener { finish() }
        findViewById<View>(R.id.calm_previous).setOnClickListener { viewModel.previous() }
        findViewById<View>(R.id.calm_play_pause).setOnClickListener { viewModel.playPause() }
        findViewById<View>(R.id.calm_next).setOnClickListener { viewModel.next() }
        findViewById<View>(R.id.calm_media_center).setOnClickListener {
            viewModel.openMediaCenter()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playback.collect { playback ->
                    findViewById<TextView>(R.id.calm_media_source).text =
                        playback.sourceLabel.ifBlank {
                            getString(LauncherR.string.media_no_source)
                        }
                    findViewById<TextView>(R.id.calm_media_title).text =
                        playback.title.ifBlank {
                            getString(LauncherR.string.media_nothing_playing)
                        }
                    findViewById<TextView>(R.id.calm_play_pause).text =
                        if (playback.isPlaying) {
                            getString(LauncherR.string.media_pause)
                        } else {
                            getString(LauncherR.string.media_play)
                        }
                    findViewById<View>(R.id.calm_previous).isEnabled = playback.canSkipPrevious
                    findViewById<View>(R.id.calm_next).isEnabled = playback.canSkipNext
                    findViewById<View>(R.id.calm_media_center).isEnabled =
                        playback.sourceComponent != null
                }
            }
        }
    }
}
