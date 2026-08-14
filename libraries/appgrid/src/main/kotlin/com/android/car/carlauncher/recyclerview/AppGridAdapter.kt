package com.android.car.carlauncher.recyclerview

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Minimal adapter seam; feature:appgrid owns the production Flow-backed adapter. */
open class AppGridAdapter : RecyclerView.Adapter<AppGridAdapter.ViewHolder>() {
    private var itemCountValue = 0

    class ViewHolder(
        parent: ViewGroup,
    ) : RecyclerView.ViewHolder(TextView(parent.context))

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(parent)

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = Unit

    override fun getItemCount(): Int = itemCountValue

    fun setItemCount(count: Int) {
        itemCountValue = count.coerceAtLeast(0)
        notifyDataSetChanged()
    }

    fun getNextRotaryFocus(
        focusedGridPosition: Int,
        direction: Int,
    ): Int =
        (focusedGridPosition + if (direction == android.view.View.FOCUS_BACKWARD) -1 else 1)
            .coerceIn(0, (itemCountValue - 1).coerceAtLeast(0))
}
