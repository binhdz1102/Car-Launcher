package com.android.car.carlauncher.pagination

import android.view.View

/** Flow-free Android view adapter; feature state remains owned by the presentation module. */
class PaginationController(
    windowBackground: View,
    private val callback: DimensionUpdateCallback,
) {
    private val measurementHelper = PageMeasurementHelper(windowBackground)

    init {
        windowBackground.viewTreeObserver.addOnGlobalLayoutListener {
            if (measurementHelper.handleWindowSizeChange(windowBackground.width, windowBackground.height)) {
                measurementHelper.getPageDimensions()?.let { page ->
                    measurementHelper.getGridDimensions()?.let { grid -> callback.notifyDimensionsUpdated(page, grid) }
                }
            }
        }
    }

    class DimensionUpdateCallback {
        private val listeners = linkedSetOf<DimensionUpdateListener>()

        fun addListener(listener: DimensionUpdateListener) {
            listeners += listener
        }

        fun notifyDimensionsUpdated(
            pageDimensions: PageMeasurementHelper.PageDimensions,
            gridDimensions: PageMeasurementHelper.GridDimensions,
        ) {
            listeners.forEach { it.onDimensionsUpdated(pageDimensions, gridDimensions) }
        }
    }

    fun interface DimensionUpdateListener {
        fun onDimensionsUpdated(
            pageDimensions: PageMeasurementHelper.PageDimensions,
            gridDimensions: PageMeasurementHelper.GridDimensions,
        )
    }
}
