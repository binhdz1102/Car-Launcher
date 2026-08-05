package com.android.car.carlauncher.feature.launcher.presentation

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.core.ui.CarUi
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp

class AppGridAdapter(
    private val onClick: (LaunchableApp) -> Unit,
) : ListAdapter<LaunchableApp, AppGridAdapter.AppViewHolder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
        AppViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_launcher_app, parent, false),
        )

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.app_icon)
        private val label: TextView = itemView.findViewById(R.id.app_label)
        private val reason: TextView = itemView.findViewById(R.id.app_disabled_reason)

        fun bind(app: LaunchableApp, onClick: (LaunchableApp) -> Unit) {
            label.text = app.label
            reason.text = app.disabledReason.orEmpty()
            reason.visibility = if (app.isEnabled) View.GONE else View.VISIBLE
            itemView.isEnabled = app.isEnabled
            itemView.alpha = if (app.isEnabled) 1f else 0.45f
            itemView.setOnClickListener { onClick(app) }
            icon.setImageDrawable(loadIcon(itemView.context, app))
            itemView.contentDescription = app.label
        }

        private fun loadIcon(context: Context, app: LaunchableApp) = runCatching {
            val component = ComponentName.unflattenFromString(app.componentName)
                ?: error("Invalid component")
            context.packageManager.getActivityInfo(component, 0).loadIcon(context.packageManager)
        }.getOrElse {
            context.packageManager.defaultActivityIcon.apply {
                setTint(Color.LTGRAY)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LaunchableApp>() {
            override fun areItemsTheSame(oldItem: LaunchableApp, newItem: LaunchableApp): Boolean =
                oldItem.componentName == newItem.componentName

            override fun areContentsTheSame(oldItem: LaunchableApp, newItem: LaunchableApp): Boolean =
                oldItem == newItem
        }
    }
}
