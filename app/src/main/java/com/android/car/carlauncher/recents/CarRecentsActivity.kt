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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.launcher.presentation.RecentsAdapter
import com.android.car.carlauncher.feature.launcher.presentation.RecentsViewModel
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
        val adapter = RecentsAdapter(viewModel::open, viewModel::remove)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        findViewById<View>(R.id.recents_clear_all).setOnClickListener { viewModel.clearAll() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tasks.collect { tasks ->
                    adapter.submitList(tasks)
                    val isEmpty = tasks.isEmpty()
                    empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    list.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
            }
        }
        Timber.tag(TAG).d("Recents activity created")
    }

    override fun onResume() {
        super.onResume()
        if (!handleDismissAction()) viewModel.refresh(currentDisplayId())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDismissAction()
    }

    private fun handleDismissAction(): Boolean {
        if (intent?.action != OPEN_RECENT_TASK_ACTION) return false
        intent.action = null
        viewModel.openTopRunningTask(currentDisplayId(), ::launchHome)
        return true
    }

    private fun launchHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun currentDisplayId(): Int = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY

    private companion object {
        const val TAG = "CarLauncher.CarRecents"
        const val OPEN_RECENT_TASK_ACTION =
            "com.android.car.carlauncher.recents.OPEN_RECENT_TASK_ACTION"
    }
}
