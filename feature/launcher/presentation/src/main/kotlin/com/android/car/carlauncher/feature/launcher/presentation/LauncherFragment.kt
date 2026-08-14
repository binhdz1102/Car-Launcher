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
import com.android.car.carlauncher.core.platform.ApplicationScope
import com.android.car.carlauncher.core.platform.CarServiceConnection
import com.android.car.carlauncher.core.platform.CoroutineDispatchers
import com.android.car.carlauncher.core.platform.PackageChangeMonitor
import com.android.car.carlauncher.core.ui.CarUi
import com.android.car.carlauncher.feature.home.domain.HomeEmbeddedTaskState
import com.android.car.carlauncher.feature.home.presentation.AndroidHomeTaskViewHost
import com.android.car.carlauncher.feature.home.presentation.HomeTaskViewEvent
import com.android.car.carlauncher.feature.home.presentation.HomeTaskViewHost
import com.android.car.carlauncher.feature.launcher.domain.MediaHistoryItem
import com.android.car.carlauncher.feature.launcher.domain.MediaPlayback
import com.android.car.carlauncher.feature.launcher.domain.MediaQueueItem
import com.android.car.carlauncher.feature.launcher.domain.MediaSource
import com.android.car.carlauncher.feature.media.domain.AssistiveCard
import com.android.car.carlauncher.feature.media.domain.CallCard
import com.android.car.carlauncher.feature.media.domain.CallCardState
import com.android.car.carlauncher.feature.media.domain.CallDurationFormatter
import com.android.car.carlauncher.feature.media.presentation.MediaArtworkDecoder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@Suppress("TooManyFunctions")
class LauncherFragment : Fragment(R.layout.fragment_launcher) {
    @Inject lateinit var carConnection: CarServiceConnection

    @Inject lateinit var packageChangeMonitor: PackageChangeMonitor

    @Inject lateinit var coroutineDispatchers: CoroutineDispatchers

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val viewModel: LauncherViewModel by viewModels()
    private var taskHost: HomeTaskViewHost? = null
    private var loadedComponent: String? = null
    private var progressFromUser = false
    private val artworkDecoder = MediaArtworkDecoder()

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
                dispatchers = coroutineDispatchers,
                applicationScope = applicationScope,
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

    fun embeddedTaskId(): Int? = taskHost?.embeddedTaskId

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
        view.findViewById<View>(R.id.media_queue_button).setOnClickListener {
            showQueue(viewModel.uiState.value.queue, view.findViewById(R.id.media_queue_button))
        }
        view.findViewById<View>(R.id.media_history_button).setOnClickListener {
            showHistory(viewModel.uiState.value.history, view.findViewById(R.id.media_history_button))
        }
        view.findViewById<View>(R.id.media_actions_button).setOnClickListener {
            showCustomActions(viewModel.uiState.value.media.customActions, view.findViewById(R.id.media_actions_button))
        }
        view.findViewById<View>(R.id.assistive_card).setOnClickListener {
            viewModel.uiState.value.assistive?.let { card ->
                viewModel.handleHomeCardAction(LauncherHomeCardAction.LaunchAssistive(card))
            }
            hostInteraction()
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
        renderHomeCards(view, state)
    }

    private fun renderHomeCards(
        view: View,
        state: LauncherUiState,
    ) {
        renderAssistive(view, state.assistive)
        state.activeCall?.let { call ->
            renderCall(view, call)
        } ?: renderMedia(view, state.media)
    }

    private fun renderAssistive(
        view: View,
        card: AssistiveCard?,
    ) {
        val cardView = view.findViewById<View>(R.id.assistive_card)
        CarUi.show(cardView, card != null)
        if (card == null) return
        view.findViewById<android.widget.TextView>(R.id.assistive_title).text = card.title
        view.findViewById<android.widget.TextView>(R.id.assistive_body).text = card.body
        view.findViewById<android.widget.TextView>(R.id.assistive_footer).text =
            card.footer
                ?: card.deviceCount
                    .takeIf { it > 0 }
                    ?.let { count ->
                        resources.getQuantityString(R.plurals.projection_devices, count, count)
                    }.orEmpty()
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
            artworkDecoder.decode(playback.artwork),
        )
        if (playback.artwork == null) {
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
        view.findViewById<android.widget.TextView>(R.id.media_previous).apply {
            text = getString(R.string.media_previous)
            isSelected = false
        }
        view.findViewById<android.widget.TextView>(R.id.media_next).apply {
            text = getString(R.string.media_next)
            isSelected = false
        }
        view.findViewById<View>(R.id.media_previous).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.Previous)
            taskHost?.restoreAfterHostInteraction()
        }
        view.findViewById<View>(R.id.media_play_pause).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.PlayPause)
            taskHost?.restoreAfterHostInteraction()
        }
        view.findViewById<View>(R.id.media_next).setOnClickListener {
            viewModel.handleMediaAction(LauncherMediaAction.Next)
            taskHost?.restoreAfterHostInteraction()
        }
        view.findViewById<android.widget.TextView>(R.id.media_play_pause).text =
            if (playback.isPlaying) getString(R.string.media_pause) else getString(R.string.media_play)
        view.findViewById<View>(R.id.media_source_button).isEnabled = true
        view.findViewById<View>(R.id.media_center).isEnabled = playback.sourceComponent != null
        view.findViewById<View>(R.id.media_queue_button).isEnabled =
            viewModel.uiState.value.queue
                .isNotEmpty()
        view.findViewById<View>(R.id.media_history_button).isEnabled =
            viewModel.uiState.value.history
                .isNotEmpty()
        view.findViewById<View>(R.id.media_actions_button).isEnabled = playback.customActions.isNotEmpty()
    }

    private fun renderCall(
        view: View,
        call: CallCard,
    ) {
        view.findViewById<android.widget.TextView>(R.id.media_source).text = call.appLabel
        view.findViewById<android.widget.TextView>(R.id.media_title).text =
            call.contactName?.takeIf(String::isNotBlank)
                ?: call.caller.ifBlank { getString(R.string.call_in_progress) }
        view.findViewById<android.widget.TextView>(R.id.media_artist).text =
            callStatus(call)
        view.findViewById<android.widget.ImageView>(R.id.media_art).apply {
            call.avatarBytes?.let { bytes ->
                setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            } ?: setImageResource(R.drawable.ic_media_note)
        }
        view.findViewById<android.widget.SeekBar>(R.id.media_progress).apply {
            isEnabled = false
            progress = 0
        }
        view.findViewById<android.widget.TextView>(R.id.media_previous).apply {
            text = getString(R.string.call_mute)
            isEnabled = true
            setOnClickListener { viewModel.handleHomeCardAction(LauncherHomeCardAction.ToggleCallMute) }
            isSelected = call.isMuted
        }
        view.findViewById<android.widget.TextView>(R.id.media_play_pause).apply {
            text = getString(R.string.call_end)
            isEnabled = true
            setOnClickListener { viewModel.handleHomeCardAction(LauncherHomeCardAction.EndCall) }
        }
        view.findViewById<android.widget.TextView>(R.id.media_next).apply {
            text = getString(R.string.call_dialpad)
            isEnabled = true
            setOnClickListener { viewModel.handleHomeCardAction(LauncherHomeCardAction.OpenDialpad) }
        }
        view.findViewById<View>(R.id.media_source_button).isEnabled = false
        view.findViewById<View>(R.id.media_center).isEnabled = false
        view.findViewById<View>(R.id.media_queue_button).isEnabled = false
        view.findViewById<View>(R.id.media_history_button).isEnabled = false
        view.findViewById<View>(R.id.media_actions_button).isEnabled = false
    }

    private fun callStatus(call: CallCard): String {
        val status =
            when (call.state) {
                CallCardState.DIALING -> getString(R.string.call_dialing)
                else -> getString(R.string.call_ongoing)
            }
        return CallDurationFormatter.format(call.connectedElapsedRealtimeMs)?.let { duration ->
            getString(R.string.call_duration_separator).let { separator -> "$status$separator$duration" }
        } ?: status
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

    private fun showQueue(
        queue: List<MediaQueueItem>,
        anchor: View,
    ) {
        if (queue.isEmpty()) return
        PopupMenu(requireContext(), anchor).apply {
            queue.forEach { item ->
                menu.add(item.title.ifBlank { item.subtitle })
            }
            show()
        }
    }

    private fun showHistory(
        history: List<MediaHistoryItem>,
        anchor: View,
    ) {
        if (history.isEmpty()) return
        PopupMenu(requireContext(), anchor).apply {
            history.forEach { item ->
                menu.add(item.title.ifBlank { item.sourceLabel })
            }
            show()
        }
    }

    private fun showCustomActions(
        actions: List<com.android.car.carlauncher.feature.launcher.domain.MediaCustomAction>,
        anchor: View,
    ) {
        if (actions.isEmpty()) return
        PopupMenu(requireContext(), anchor).apply {
            actions.forEachIndexed { index, action ->
                menu.add(0, index, index, action.title)
            }
            setOnMenuItemClickListener { item ->
                actions.getOrNull(item.itemId)?.let { action ->
                    viewModel.handleMediaAction(LauncherMediaAction.CustomAction(action))
                }
                true
            }
            show()
        }
    }

    private fun openAppGrid() {
        startActivity(Intent(ACTION_APP_GRID).setPackage(requireContext().packageName))
    }

    private companion object {
        const val TAG = "CarLauncher.LauncherFragment"
    }
}
