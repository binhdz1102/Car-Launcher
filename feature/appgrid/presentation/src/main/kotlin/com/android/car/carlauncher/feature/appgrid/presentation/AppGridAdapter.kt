package com.android.car.carlauncher.feature.appgrid.presentation

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.feature.appgrid.domain.AppGridAvailability
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItem
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItemType
import com.android.car.carlauncher.feature.appgrid.domain.AppGridOrientation
import com.android.car.carlauncher.feature.appgrid.domain.AppGridPaging

class AppGridAdapter(
    private val onClick: (AppGridItem) -> Unit,
    private val onLongClick: (AppGridItem) -> Boolean,
) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {
    private val items = mutableListOf<AppGridItem>()
    private var cellWidth = ViewGroup.LayoutParams.WRAP_CONTENT
    private var cellHeight = ViewGroup.LayoutParams.WRAP_CONTENT
    private var orientation = AppGridOrientation.HORIZONTAL
    private var rtl = false
    private var columns = DEFAULT_COLUMNS
    private var rows = DEFAULT_ROWS

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
        val businessIndex = AppGridPaging.gridPositionToAdapterIndex(position, columns, rows, orientation, rtl)
        if (businessIndex in items.indices) {
            holder.bind(items[businessIndex], onClick, onLongClick)
        } else {
            // AOSP pads the last page with empty holders. They must still occupy a grid cell, but
            // must not be focusable/clickable or address a business item beyond the list.
            holder.bindPlaceholder()
        }
        holder.itemView.layoutParams =
            holder.itemView.layoutParams.apply {
                width = cellWidth
                height = cellHeight
            }
    }

    override fun getItemCount(): Int = AppGridPaging.pagedItemCount(items.size, columns, rows)

    fun submitItems(next: List<AppGridItem>) {
        if (items == next) return
        items.clear()
        items.addAll(next)
        // The adapter count includes page padding, while the source list does not. A plain
        // dataset refresh keeps RecyclerView's old/new counts coherent across a page-boundary
        // change and mirrors AOSP's padded grid update semantics.
        notifyDataSetChanged()
    }

    fun move(
        fromPosition: Int,
        toPosition: Int,
    ): Boolean {
        val fromIndex = AppGridPaging.gridPositionToAdapterIndex(fromPosition, columns, rows, orientation, rtl)
        if (fromIndex !in items.indices) return false
        val mappedToIndex = AppGridPaging.gridPositionToAdapterIndex(toPosition, columns, rows, orientation, rtl)
        val toIndex = mappedToIndex.coerceIn(0, items.size - 1)
        items.add(toIndex, items.removeAt(fromIndex))
        notifyDataSetChanged()
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

    fun setPaging(
        orientation: AppGridOrientation,
        rtl: Boolean,
        columns: Int,
        rows: Int,
    ) {
        require(columns > 0 && rows > 0) { "Grid dimensions must be positive." }
        this.orientation = orientation
        this.rtl = rtl
        this.columns = columns
        this.rows = rows
        notifyDataSetChanged()
    }

    inner class AppViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.app_icon)
        private val label: TextView = itemView.findViewById(R.id.app_name)
        private val reason: TextView = itemView.findViewById(R.id.app_grid_reason)

        fun bind(
            item: AppGridItem,
            onClick: (AppGridItem) -> Unit,
            onLongClick: (AppGridItem) -> Boolean,
        ) {
            itemView.alpha = 1f
            itemView.isFocusable = true
            itemView.isClickable = true
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
            // AOSP exposes the label through app_name and makes the icon itself actionable.
            // Keeping the root content description empty avoids a duplicate accessibility node.
            itemView.contentDescription = null
            icon.isClickable = true
            icon.isFocusable = false
            icon.setOnClickListener { onClick(item) }
            icon.setOnLongClickListener { onLongClick(item) }
        }

        fun bindPlaceholder() {
            itemView.alpha = 0f
            itemView.isEnabled = false
            itemView.isFocusable = false
            itemView.isClickable = false
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener(null)
            itemView.contentDescription = null
            icon.isClickable = false
            icon.isFocusable = false
            icon.setOnClickListener(null)
            icon.setOnLongClickListener(null)
            icon.setImageDrawable(null)
            label.text = null
            reason.text = null
            reason.visibility = View.GONE
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
                    val launcherInfos =
                        context
                            .getSystemService(LauncherApps::class.java)
                            // LauncherActivityInfo owns AAOS badging and density normalization.
                            // Query the complete user inventory: package-filtered queries can omit
                            // alias activities on API 37, which silently falls back to the raw
                            // PackageManager icon and loses the stock badge/background.
                            ?.getActivityList(null, Process.myUserHandle())
                    val launcherInfo = launcherInfos?.firstOrNull { it.componentName == component }
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
        const val DEFAULT_COLUMNS = 5
        const val DEFAULT_ROWS = 4
        const val DISABLED_ALPHA = 0.45f
    }
}
