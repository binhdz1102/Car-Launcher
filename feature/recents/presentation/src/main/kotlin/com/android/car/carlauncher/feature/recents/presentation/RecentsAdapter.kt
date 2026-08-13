package com.android.car.carlauncher.feature.recents.presentation

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.feature.recents.domain.RecentTask

/** XML task-card adapter. Bitmap decoding stays at the UI boundary, away from domain models. */
class RecentsAdapter(
    private val onLaunch: (RecentTask) -> Unit,
    private val onDismiss: (RecentTask) -> Unit,
) : ListAdapter<RecentTask, RecentsAdapter.TaskViewHolder>(DIFF) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): TaskViewHolder =
        TaskViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_recent_task, parent, false),
            onLaunch,
            onDismiss,
        )

    override fun onBindViewHolder(
        holder: TaskViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        itemView: View,
        private val onLaunch: (RecentTask) -> Unit,
        private val onDismiss: (RecentTask) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon = itemView.findViewById<ImageView>(R.id.recent_icon)
        private val thumbnail = itemView.findViewById<ImageView>(R.id.recent_thumbnail)
        private val label = itemView.findViewById<TextView>(R.id.recent_label)
        private val disabledReason = itemView.findViewById<TextView>(R.id.recent_disabled_reason)
        private val dismiss = itemView.findViewById<TextView>(R.id.recent_remove)

        fun bind(task: RecentTask) {
            label.text = task.label
            icon.setImageDrawable(loadIcon(itemView.context, task))
            thumbnail.setImageBitmap(
                task.thumbnailBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) },
            )
            disabledReason.visibility = if (task.isEnabled) View.GONE else View.VISIBLE
            itemView.contentDescription = task.label
            itemView.isEnabled = task.isEnabled
            itemView.alpha = if (task.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
            itemView.setOnClickListener { if (task.isEnabled) onLaunch(task) }
            dismiss.setOnClickListener { onDismiss(task) }
        }

        private fun loadIcon(
            context: Context,
            task: RecentTask,
        ) = runCatching {
            val applicationInfo = context.packageManager.getApplicationInfo(task.packageName, 0)
            applicationInfo.loadIcon(context.packageManager)
        }.getOrElse {
            context.packageManager.defaultActivityIcon
                .mutate()
                .apply { setTint(Color.LTGRAY) }
        }
    }

    private companion object {
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.45f
        val DIFF =
            object : DiffUtil.ItemCallback<RecentTask>() {
                override fun areItemsTheSame(
                    oldItem: RecentTask,
                    newItem: RecentTask,
                ): Boolean = oldItem.taskId == newItem.taskId

                override fun areContentsTheSame(
                    oldItem: RecentTask,
                    newItem: RecentTask,
                ): Boolean = oldItem == newItem
            }
    }
}
