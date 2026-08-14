package com.android.car.carlauncher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.appgridlib.R
import com.android.car.carlauncher.pagination.PageIndexingHelper
import com.android.car.carlauncher.pagination.PageMeasurementHelper
import com.android.car.carlauncher.recyclerview.AppGridAdapter
import com.android.car.carlauncher.recyclerview.PageMarginDecoration

/** RecyclerView host preserving the stock AppGrid custom-view contract. */
class AppGridRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs),
        com.android.car.carlauncher.pagination.PaginationController.DimensionUpdateListener {
        private val pageOrientation =
            if (resources.getBoolean(R.bool.use_vertical_app_grid)) {
                AppGridConstants.PageOrientation.VERTICAL
            } else {
                AppGridConstants.PageOrientation.HORIZONTAL
            }
        private var pendingAdapter: AppGridAdapter? = null
        private var indexingHelper: PageIndexingHelper? = null
        private var numOfRows = 0
        private var numOfCols = 0
        private var previousRotaryDirection = View.FOCUS_FORWARD

        override fun setAdapter(adapter: Adapter<*>?) {
            check(adapter == null || adapter is AppGridAdapter) {
                "Expected Adapter of type AppGridAdapter"
            }
            pendingAdapter = adapter as? AppGridAdapter
        }

        override fun focusSearch(
            focused: View,
            direction: Int,
        ): View? {
            var result = focused
            val current = findContainingViewHolder(focused)?.absoluteAdapterPosition
            val adapter = pendingAdapter
            if (current != null && adapter != null && scrollState == SCROLL_STATE_IDLE) {
                val next = adapter.getNextRotaryFocus(current, direction)
                val blockSize = (numOfCols * numOfRows).coerceAtLeast(1)
                if (current / blockSize == next / blockSize) {
                    result = getChildAt(next % blockSize) ?: focused
                } else if (AppGridConstants.isHorizontal(pageOrientation)) {
                    val dx = if (direction == View.FOCUS_FORWARD) width else -width
                    smoothScrollBy(dx, 0)
                    previousRotaryDirection = direction
                } else {
                    val dy = if (direction == View.FOCUS_FORWARD) height else -height
                    smoothScrollBy(0, dy)
                    previousRotaryDirection = direction
                }
            }
            return result
        }

        fun maybeHandleRotaryFocus() {
            if (!isInTouchMode() && childCount > 0) {
                getChildAt(if (previousRotaryDirection == View.FOCUS_FORWARD) 0 else childCount - 1)
                    ?.requestFocus()
            }
        }

        fun getPageIndexingHelper(): PageIndexingHelper? = indexingHelper

        fun getNumOfRows(): Int = numOfRows

        fun getNumOfCols(): Int = numOfCols

        @VisibleForTesting
        fun forceAttachAdapter(
            numOfRows: Int,
            numOfCols: Int,
        ) {
            this.numOfRows = numOfRows
            this.numOfCols = numOfCols
            super.setAdapter(pendingAdapter)
        }

        override fun onDimensionsUpdated(
            pageDimensions: PageMeasurementHelper.PageDimensions,
            gridDimensions: PageMeasurementHelper.GridDimensions,
        ) {
            numOfRows = gridDimensions.mNumOfRows
            numOfCols = gridDimensions.mNumOfCols
            (layoutManager as? GridLayoutManager)?.spanCount =
                if (AppGridConstants.isHorizontal(pageOrientation)) numOfRows else numOfCols
            indexingHelper = PageIndexingHelper(numOfCols, numOfRows, pageOrientation)
            layoutParams =
                layoutParams?.apply {
                    width = pageDimensions.recyclerViewWidthPx
                    height = pageDimensions.recyclerViewHeightPx
                }
            pendingAdapter?.let { super.setAdapter(it) }
            addItemDecoration(
                PageMarginDecoration(
                    pageDimensions.marginHorizontalPx,
                    pageDimensions.marginVerticalPx,
                    indexingHelper!!,
                ),
            )
        }
    }
