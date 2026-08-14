package com.android.car.carlauncher.recyclerview

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.AppGridConstants

class AppGridLayoutManager(
    context: Context,
    pageOrientation: Int,
) : GridLayoutManager(
        context,
        DEFAULT_SPAN_COUNT,
        if (AppGridConstants.isHorizontal(pageOrientation)) HORIZONTAL else VERTICAL,
        false,
    ) {
    private companion object {
        const val DEFAULT_SPAN_COUNT = 3
    }

    private var shouldLayoutChildrenEnabled: Boolean = true

    fun setShouldLayoutChildren(shouldLayoutChildren: Boolean) {
        shouldLayoutChildrenEnabled = shouldLayoutChildren
    }

    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ) {
        if (shouldLayoutChildrenEnabled) super.onLayoutChildren(recycler, state)
    }
}
