package com.android.car.docklib.view

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.car.docklib.DockInterface
import com.android.car.docklib.data.DockAppItem

/** Small XML/View adapter that preserves Dock item actions without imposing host styling. */
class DockAdapter(
    private val dockController: DockInterface,
) : RecyclerView.Adapter<DockAdapter.DockViewHolder>() {
    private var items: List<DockAppItem> = emptyList()

    fun submitList(newItems: List<DockAppItem>) {
        items = newItems.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DockViewHolder {
        val container =
            LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumWidth = 80
                minimumHeight = 80
                setPadding(12, 8, 12, 8)
            }
        val icon =
            ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
            }
        val label =
            TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                maxLines = 1
            }
        container.addView(icon)
        container.addView(label)
        return DockViewHolder(container, icon, label, dockController)
    }

    override fun onBindViewHolder(
        holder: DockViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class DockViewHolder(
        container: LinearLayout,
        private val icon: ImageView,
        private val label: TextView,
        private val dockController: DockInterface,
    ) : RecyclerView.ViewHolder(container) {
        fun bind(item: DockAppItem) {
            icon.setImageDrawable(item.icon)
            label.text = item.name
            itemView.contentDescription = item.name
            itemView.setOnClickListener {
                dockController.launchApp(item.component, item.isMediaApp)
                dockController.appLaunched(item.component)
            }
            itemView.setOnLongClickListener {
                dockController.appUnpinned(item.id)
                true
            }
        }
    }
}
