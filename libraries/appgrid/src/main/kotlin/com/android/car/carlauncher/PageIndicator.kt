package com.android.car.carlauncher

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.car.carlauncher.pagination.PageMeasurementHelper

/** Scroll indicator view retained for AOSP host layouts. */
class PageIndicator
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs),
        com.android.car.carlauncher.pagination.PaginationController.DimensionUpdateListener {
        private var container: FrameLayout? = null
        private var pageCount = 1
        private var offset = 0

        fun setContainer(container: FrameLayout) {
            this.container = container
        }

        override fun onDimensionsUpdated(
            pageDimens: PageMeasurementHelper.PageDimensions,
            gridDimens: PageMeasurementHelper.GridDimensions,
        ) {
            container?.layoutParams =
                container?.layoutParams?.apply {
                    width = pageDimens.pageIndicatorWidthPx
                    height = pageDimens.pageIndicatorHeightPx
                }
            updatePageCount(pageCount)
        }

        fun updatePageCount(pageCount: Int) {
            this.pageCount = pageCount.coerceAtLeast(1)
            updateOffset(offset)
        }

        fun updateOffset(appGridOffset: Int) {
            offset = appGridOffset
            (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.leftMargin = offset / pageCount
                layoutParams = params
            }
        }

        fun animateAppearance() {
            animate().alpha(1f).setDuration(ANIMATION_DURATION_MS).start()
        }

        fun animateFading() {
            animate().alpha(0f).setDuration(ANIMATION_DURATION_MS).start()
        }

        private companion object {
            const val ANIMATION_DURATION_MS = 200L
        }
    }
