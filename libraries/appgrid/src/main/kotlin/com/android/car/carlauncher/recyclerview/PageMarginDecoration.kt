package com.android.car.carlauncher.recyclerview

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.AppGridConstants
import com.android.car.carlauncher.pagination.PageIndexingHelper

class PageMarginDecoration(
    private val marginHorizontalPx: Int,
    private val marginVerticalPx: Int,
    private val indexingHelper: PageIndexingHelper,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.set(0, 0, 0, 0)
        when (indexingHelper.getOffsetBoundDirection(parent.getChildAdapterPosition(view))) {
            AppGridConstants.AppItemBoundDirection.LEFT -> outRect.left = marginHorizontalPx
            AppGridConstants.AppItemBoundDirection.RIGHT -> outRect.right = marginHorizontalPx
            AppGridConstants.AppItemBoundDirection.TOP -> outRect.top = marginVerticalPx
            AppGridConstants.AppItemBoundDirection.BOTTOM -> outRect.bottom = marginVerticalPx
        }
    }
}
