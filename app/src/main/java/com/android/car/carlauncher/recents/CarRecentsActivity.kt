package com.android.car.carlauncher.recents

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.recents.presentation.RecentsAdapter
import com.android.car.carlauncher.feature.recents.presentation.RecentsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/** XML recents surface backed by the platform task manager. */
@AndroidEntryPoint
class CarRecentsActivity : AppCompatActivity() {
    private val viewModel: RecentsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recents)
        applySystemBarInsets()

        val empty = findViewById<TextView>(R.id.recents_empty)
        val list = findViewById<RecyclerView>(R.id.recents_list)
        val adapter = RecentsAdapter(viewModel::launch, viewModel::dismiss)
        list.layoutManager = GridLayoutManager(this, RECENTS_SPAN_COUNT)
        list.adapter = adapter
        findViewById<View>(R.id.recents_clear_all).setOnClickListener { viewModel.clearAll() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.tasks)
                    val isEmpty = state.tasks.isEmpty()
                    empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    list.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
            }
        }
        Timber.tag(TAG).d("Recents activity created")
    }

    override fun onResume() {
        super.onResume()
        if (!handleDismissAction()) viewModel.refresh(currentDisplayTarget())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDismissAction()
    }

    private fun handleDismissAction(): Boolean {
        if (intent?.action != OPEN_RECENT_TASK_ACTION) return false
        intent.action = null
        viewModel.launchTopRunningTask(currentDisplayTarget(), ::launchHome)
        return true
    }

    private fun launchHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun currentDisplayTarget(): DisplayTarget {
        val displayId = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        return DisplayTarget(
            displayId = displayId,
            isDefaultDisplay = displayId == android.view.Display.DEFAULT_DISPLAY,
        )
    }

    private companion object {
        const val TAG = "CarLauncher.CarRecents"
        const val RECENTS_SPAN_COUNT = 3
        const val OPEN_RECENT_TASK_ACTION =
            "com.android.car.carlauncher.recents.OPEN_RECENT_TASK_ACTION"
    }
}
