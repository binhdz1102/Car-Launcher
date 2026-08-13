package com.android.car.carlauncher.feature.appgrid.presentation

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.content.pm.LauncherApps
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.feature.appgrid.domain.AppGridAvailability
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItem
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItemType

class AppGridAdapter(
    private val onClick: (AppGridItem) -> Unit,
    private val onLongClick: (AppGridItem) -> Boolean,
) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {
    private val items = mutableListOf<AppGridItem>()
    private var cellWidth = ViewGroup.LayoutParams.WRAP_CONTENT
    private var cellHeight = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AppViewHolder =
        AppViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_app_grid_app, parent, false),
        )

    override fun onBindViewHolder(
        holder: AppViewHolder,
        position: Int,
    ) {
        holder.bind(items[position], onClick, onLongClick)
        holder.itemView.layoutParams =
            holder.itemView.layoutParams.apply {
                width = cellWidth
                height = cellHeight
            }
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(next: List<AppGridItem>) {
        if (items == next) return
        val previous = items.toList()
        val diff =
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = previous.size

                    override fun getNewListSize(): Int = next.size

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previous[oldItemPosition].component == next[newItemPosition].component

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previous[oldItemPosition] == next[newItemPosition]
                },
            )
        items.clear()
        items.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    fun move(
        fromPosition: Int,
        toPosition: Int,
    ): Boolean {
        if (fromPosition !in items.indices || toPosition !in items.indices) return false
        items.add(toPosition, items.removeAt(fromPosition))
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun componentOrder(): List<LauncherComponent> = items.map(AppGridItem::component)

    fun updateCellSize(
        width: Int,
        height: Int,
    ) {
        if (cellWidth == width && cellHeight == height) return
        cellWidth = width
        cellHeight = height
        notifyDataSetChanged()
    }

    inner class AppViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.app_grid_icon)
        private val label: TextView = itemView.findViewById(R.id.app_grid_label)
        private val reason: TextView = itemView.findViewById(R.id.app_grid_reason)

        fun bind(
            item: AppGridItem,
            onClick: (AppGridItem) -> Unit,
            onLongClick: (AppGridItem) -> Boolean,
        ) {
            label.text = item.label
            val enabled =
                item.availability == AppGridAvailability.AVAILABLE ||
                    item.availability == AppGridAvailability.TOS_REVIEW_REQUIRED
            reason.text = itemView.context.getString(item.availability.reasonText)
            reason.visibility = if (enabled) View.GONE else View.VISIBLE
            itemView.isEnabled = enabled
            itemView.alpha = if (enabled) 1f else DISABLED_ALPHA
            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener { onLongClick(item) }
            icon.setImageDrawable(loadIcon(itemView.context, item))
            itemView.contentDescription = item.label
        }

        private fun loadIcon(
            context: Context,
            item: AppGridItem,
        ) = runCatching {
            val component = ComponentName(item.component.packageName, item.component.className)
            when (item.type) {
                AppGridItemType.MEDIA_SERVICE ->
                    context.packageManager.getServiceInfo(component, 0).loadIcon(context.packageManager)
                else -> {
                    // Stock AppGridRepository obtains activity icons from LauncherActivityInfo,
                    // including the profile badge/density normalization of getBadgedIcon(0).
                    val launcherInfo = context.getSystemService(LauncherApps::class.java)
                        ?.getActivityList(component.packageName, Process.myUserHandle())
                        ?.firstOrNull { it.componentName == component }
                    launcherInfo?.getBadgedIcon(0)
                        ?: context.packageManager.getActivityInfo(component, 0).loadIcon(context.packageManager)
                }
            }
        }.getOrElse {
            context.packageManager.defaultActivityIcon
                .mutate()
                .apply { setTint(Color.LTGRAY) }
        }
    }

    private val AppGridAvailability.reasonText: Int
        get() =
            when (this) {
                AppGridAvailability.AVAILABLE -> R.string.app_grid_no_reason
                AppGridAvailability.REQUIRES_DISTRACTION_OPTIMIZATION -> R.string.app_grid_unavailable_while_driving
                AppGridAvailability.CAR_SERVICE_UNAVAILABLE -> R.string.app_grid_safety_service_unavailable
                AppGridAvailability.TOS_REVIEW_REQUIRED -> R.string.app_grid_tos_required
            }

    private companion object {
        const val DISABLED_ALPHA = 0.45f
    }
}
