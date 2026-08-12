package com.android.car.carlauncher.feature.launcher.presentation

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.core.platform.PackageChangeMonitor
import com.android.car.carlauncher.core.ui.CarUi
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskState
import com.android.car.carlauncher.feature.home.presentation.AndroidHomeTaskViewHost
import com.android.car.carlauncher.feature.home.presentation.HomeTaskViewEvent
import com.android.car.carlauncher.feature.home.presentation.HomeTaskViewHost
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LauncherFragment : Fragment(R.layout.fragment_launcher) {
    @Inject lateinit var carConnection: CarServiceConnection

    @Inject lateinit var packageChangeMonitor: PackageChangeMonitor

    private val viewModel: LauncherViewModel by viewModels()
    private var taskHost: HomeTaskViewHost? = null
    private var loadedComponent: String? = null
    private var progressFromUser = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val host =
            AndroidHomeTaskViewHost(
                activity = requireActivity(),
                carConnection = carConnection,
                packageChangeMonitor = packageChangeMonitor,
            )
        taskHost = host
        view.findViewById<android.widget.FrameLayout>(R.id.task_view_container).addView(host.view)

        bindActions(view)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(view, state) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.events.collect { event ->
                    when (event) {
                        is HomeTaskViewEvent.TaskAppeared ->
                            viewModel.onTaskAppeared(event.componentName)
                        is HomeTaskViewEvent.TaskInfoChanged ->
                            viewModel.onTaskInfoChanged(event.componentName)
                        is HomeTaskViewEvent.Failure ->
                            viewModel.onEmbeddedFailure(event.title, event.message)
                        HomeTaskViewEvent.Recovering -> viewModel.onTaskViewRecovering()
                    }
                }
            }
        }
        Timber.tag(TAG).d("Launcher fragment created")
    }

    override fun onDestroyView() {
        taskHost?.release()
        taskHost = null
        loadedComponent = null
        super.onDestroyView()
    }

    fun handleHostIntent(intent: Intent?) {
        taskHost?.onHostNewIntent()
        viewModel.handleHostIntent(intent)
    }

    private fun bindActions(view: View) {
        view.findViewById<View>(R.id.open_apps).setOnClickListener { openAppGrid() }
        view.findViewById<View>(R.id.task_error_apps).setOnClickListener { openAppGrid() }
        view.findViewById<View>(R.id.task_error_retry).setOnClickListener {
            viewModel.selectNavigation()
        }

        val hostInteraction: () -> Unit = {
            taskHost?.restoreAfterHostInteraction()
        }
        view.findViewById<View>(R.id.media_previous).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.Previous)
            hostInteraction()
        }
        view.findViewById<View>(R.id.media_play_pause).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.PlayPause)
            hostInteraction()
        }
        view.findViewById<View>(R.id.media_next).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.Next)
            hostInteraction()
        }
        view.findViewById<View>(R.id.media_center).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.OpenCenter)
            hostInteraction()
        }
        view.findViewById<View>(R.id.media_source_button).setOnClickListener {
            val state = viewModel.uiState.value
            showMediaSources(view, state.sources, hostInteraction)
        }
        view.findViewById<android.widget.SeekBar>(R.id.media_progress).setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    progressFromUser = fromUser
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                    progressFromUser = true
                }

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    if (progressFromUser) {
                        viewModel.handleMediaAction(
                            LauncherMediaAction.Seek(seekBar?.progress?.toLong() ?: 0L),
                        )
                        hostInteraction()
                    }
                    progressFromUser = false
                }
            },
        )
    }

    private fun render(
        view: View,
        state: LauncherUiState,
    ) {
        if (state.currentTarget == null && state.taskState is HomeEmbeddedTaskState.Error) {
            loadedComponent = null
        }
        state.currentTarget?.let { target ->
            if (loadedComponent != target.componentName) {
                loadedComponent = target.componentName
                taskHost?.load(target)
            }
        }

        val error = state.error
        val isError = error != null || state.taskState is HomeEmbeddedTaskState.Error
        taskHost?.setVisible(!isError)
        CarUi.show(view.findViewById(R.id.task_error), isError)
        CarUi.show(view.findViewById(R.id.task_loading), state.taskState is HomeEmbeddedTaskState.Loading)
        if (error != null) {
            view.findViewById<android.widget.TextView>(R.id.task_error_title).text = error.title
            view.findViewById<android.widget.TextView>(R.id.task_error_message).text = error.message
        }
        renderMedia(view, state.media)
    }

    private fun renderMedia(
        view: View,
        playback: MediaPlayback,
    ) {
        view.findViewById<android.widget.TextView>(R.id.media_source).text =
            playback.sourceLabel.ifBlank { getString(R.string.media_no_source) }
        view.findViewById<android.widget.TextView>(R.id.media_title).text =
            playback.title.ifBlank { getString(R.string.media_nothing_playing) }
        view.findViewById<android.widget.TextView>(R.id.media_artist).text =
            playback.artist.ifBlank { getString(R.string.media_choose_source) }
        view.findViewById<android.widget.ImageView>(R.id.media_art).setImageBitmap(
            playback.artworkBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
        )
        if (playback.artworkBytes == null) {
            view
                .findViewById<android.widget.ImageView>(R.id.media_art)
                .setImageResource(R.drawable.ic_media_note)
        }
        val progress = view.findViewById<android.widget.SeekBar>(R.id.media_progress)
        progress.max = playback.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (!progressFromUser) {
            progress.progress = playback.positionMs.coerceIn(0L, progress.max.toLong()).toInt()
        }
        progress.isEnabled = playback.canSeek && playback.durationMs > 0
        view.findViewById<View>(R.id.media_previous).isEnabled = playback.canSkipPrevious
        view.findViewById<View>(R.id.media_play_pause).isEnabled = playback.sourcePackage != null
        view.findViewById<View>(R.id.media_next).isEnabled = playback.canSkipNext
        view.findViewById<android.widget.TextView>(R.id.media_play_pause).text =
            if (playback.isPlaying) getString(R.string.media_pause) else getString(R.string.media_play)
        view.findViewById<View>(R.id.media_source_button).isEnabled = true
        view.findViewById<View>(R.id.media_center).isEnabled = playback.sourceComponent != null
    }

    private fun showMediaSources(
        view: View,
        sources: List<MediaSource>,
        hostInteraction: () -> Unit,
    ) {
        if (sources.isEmpty()) return
        PopupMenu(requireContext(), view.findViewById(R.id.media_source_button))
            .apply {
                sources.forEachIndexed { index, source ->
                    menu.add(0, index, index, source.label)
                }
                setOnMenuItemClickListener { item ->
                    sources.getOrNull(item.itemId)?.let { source ->
                        viewModel.handleMediaAction(LauncherMediaAction.SelectSource(source))
                    }
                    hostInteraction()
                    true
                }
            }.show()
    }

    private fun openAppGrid() {
        startActivity(Intent(ACTION_APP_GRID).setPackage(requireContext().packageName))
    }

    private companion object {
        const val TAG = "CarLauncher.LauncherFragment"
    }
}
