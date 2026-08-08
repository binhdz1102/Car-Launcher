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
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LaunchableAppDisabledReason
import com.android.car.carlauncher.feature.launcher.domain.LaunchableAppType

class AppGridAdapter(
    private val onClick: (LaunchableApp) -> Unit,
) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {
    private val items = mutableListOf<LaunchableApp>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AppViewHolder =
        AppViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_launcher_app, parent, false),
        )

    override fun onBindViewHolder(
        holder: AppViewHolder,
        position: Int,
    ) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(apps: List<LaunchableApp>) {
        if (items == apps) return
        val previous = items.toList()
        val diff =
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = previous.size

                    override fun getNewListSize(): Int = apps.size

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean =
                        previous[oldItemPosition].componentName ==
                            apps[newItemPosition].componentName

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previous[oldItemPosition] == apps[newItemPosition]
                },
            )
        items.clear()
        items.addAll(apps)
        diff.dispatchUpdatesTo(this)
    }

    fun move(
        fromPosition: Int,
        toPosition: Int,
    ): Boolean {
        if (fromPosition !in items.indices || toPosition !in items.indices) return false
        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun componentOrder(): List<String> = items.map(LaunchableApp::componentName)

    class AppViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.app_icon)
        private val label: TextView = itemView.findViewById(R.id.app_label)
        private val reason: TextView = itemView.findViewById(R.id.app_disabled_reason)

        fun bind(
            app: LaunchableApp,
            onClick: (LaunchableApp) -> Unit,
        ) {
            label.text = app.label
            reason.text =
                app.disabledReason
                    ?.let { disabledReason ->
                        itemView.context.getString(
                            when (disabledReason) {
                                LaunchableAppDisabledReason.NOT_DISTRACTION_OPTIMIZED ->
                                    R.string.app_disabled_not_distraction_optimized
                                LaunchableAppDisabledReason.SAFETY_SERVICE_UNAVAILABLE ->
                                    R.string.app_disabled_safety_service
                            },
                        )
                    }.orEmpty()
            reason.visibility = if (app.isEnabled) View.GONE else View.VISIBLE
            itemView.isEnabled = app.isEnabled
            itemView.alpha = if (app.isEnabled) 1f else DISABLED_ALPHA
            itemView.setOnClickListener { if (app.isEnabled) onClick(app) }
            icon.setImageDrawable(loadIcon(itemView.context, app))
            itemView.contentDescription = app.label
        }

        private fun loadIcon(
            context: Context,
            app: LaunchableApp,
        ) = runCatching {
            val component =
                ComponentName.unflattenFromString(app.componentName)
                    ?: error("Invalid component")
            when (app.type) {
                LaunchableAppType.ACTIVITY ->
                    context.packageManager.getActivityInfo(component, 0)
                LaunchableAppType.MEDIA_SERVICE ->
                    context.packageManager.getServiceInfo(component, 0)
            }.loadIcon(context.packageManager)
        }.getOrElse {
            context.packageManager.defaultActivityIcon.mutate().apply {
                setTint(Color.LTGRAY)
            }
        }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.45f
    }
}
