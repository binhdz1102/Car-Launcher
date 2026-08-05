package com.android.car.carlauncher.recents

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Minimal XML recents surface driven by the platform task manager. */
class CarRecentsActivity : AppCompatActivity() {
    private val activityManager by lazy { getSystemService(ActivityManager::class.java) }
    private lateinit var adapter: RecentTaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recents)
        adapter = RecentTaskAdapter(::openTask, ::removeTask)
        findViewById<RecyclerView>(R.id.recents_list).apply {
            layoutManager = LinearLayoutManager(this@CarRecentsActivity)
            adapter = this@CarRecentsActivity.adapter
        }
        findViewById<View>(R.id.recents_clear_all).setOnClickListener { clearAll() }
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    private fun refreshTasks() {
        lifecycleScope.launch {
            val tasks = withContext(Dispatchers.IO) { loadTasks() }
            adapter.submitList(tasks)
            findViewById<View>(R.id.recents_empty).visibility =
                if (tasks.isEmpty()) View.VISIBLE else View.GONE
            findViewById<View>(R.id.recents_list).visibility =
                if (tasks.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun loadTasks(): List<RecentTask> = runCatching {
        activityManager.getRecentTasks(
            MAX_TASKS,
            ActivityManager.RECENT_WITH_EXCLUDED,
        ).asSequence()
            .filter { task ->
                val packageName = task.baseIntent?.component?.packageName
                packageName != null && packageName != packageNameOfThisApp() &&
                    packageName != "com.android.systemui"
            }
            .mapNotNull { task ->
                val component = task.baseIntent?.component ?: return@mapNotNull null
                val label = runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(component.packageName, 0),
                    ).toString()
                }.getOrDefault(component.packageName)
                val icon = runCatching {
                    packageManager.getApplicationIcon(component.packageName)
                }.getOrNull()
                RecentTask(task.taskId, component.packageName, label, icon, task.baseIntent)
            }
            .distinctBy(RecentTask::taskId)
            .toList()
    }.onFailure { Timber.tag(TAG).w(it, "Unable to read recent tasks") }.getOrDefault(emptyList())

    private fun openTask(task: RecentTask) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                ActivityTaskManager.getService().startActivityFromRecents(
                    task.taskId,
                    ActivityOptions.makeBasic().toBundle(),
                )
            }.onFailure {
                Timber.tag(TAG).w(it, "startActivityFromRecents failed; using base intent")
                task.baseIntent?.let { intent ->
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    private fun removeTask(task: RecentTask) {
        runCatching { ActivityTaskManager.getService().removeTask(task.taskId) }
            .onFailure { Timber.tag(TAG).w(it, "Unable to remove recent task=%d", task.taskId) }
        refreshTasks()
    }

    private fun clearAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            loadTasks().forEach { task ->
                runCatching { ActivityTaskManager.getService().removeTask(task.taskId) }
            }
            withContext(Dispatchers.Main) { refreshTasks() }
        }
    }

    private fun packageNameOfThisApp(): String = packageName

    private data class RecentTask(
        val taskId: Int,
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val baseIntent: Intent?,
    )

    private class RecentTaskAdapter(
        private val onOpen: (RecentTask) -> Unit,
        private val onRemove: (RecentTask) -> Unit,
    ) : ListAdapter<RecentTask, RecentTaskAdapter.TaskViewHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder =
            TaskViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_recent_task, parent, false),
                onOpen,
                onRemove,
            )

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class TaskViewHolder(
            itemView: View,
            private val onOpen: (RecentTask) -> Unit,
            private val onRemove: (RecentTask) -> Unit,
        ) : RecyclerView.ViewHolder(itemView) {
            private val icon = itemView.findViewById<ImageView>(R.id.recent_icon)
            private val label = itemView.findViewById<TextView>(R.id.recent_label)
            private val remove = itemView.findViewById<TextView>(R.id.recent_remove)

            fun bind(task: RecentTask) {
                icon.setImageDrawable(task.icon)
                label.text = task.label
                itemView.setOnClickListener { onOpen(task) }
                remove.setOnClickListener { onRemove(task) }
            }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<RecentTask>() {
                override fun areItemsTheSame(oldItem: RecentTask, newItem: RecentTask): Boolean =
                    oldItem.taskId == newItem.taskId

                override fun areContentsTheSame(oldItem: RecentTask, newItem: RecentTask): Boolean =
                    oldItem == newItem
            }
        }
    }

    private companion object {
        const val MAX_TASKS = 30
        const val TAG = "CarLauncher.CarRecents"
    }
}
