package com.android.car.carlauncher.pagination

import android.view.View
import com.android.car.carlauncher.AppGridConstants
import com.android.car.carlauncher.appgridlib.R

/** Stable value objects used by the AppGrid layout and page indicator. */
class PageMeasurementHelper(
    private val windowBackground: View,
) {
    private val pageOrientation =
        if (windowBackground.resources.getBoolean(R.bool.use_vertical_app_grid)) {
            AppGridConstants.PageOrientation.VERTICAL
        } else {
            AppGridConstants.PageOrientation.HORIZONTAL
        }
    private val useDefinedDimensions =
        windowBackground.resources.getBoolean(R.bool.use_defined_app_grid_dimensions)
    private val definedWidth =
        windowBackground.resources.getDimensionPixelSize(R.dimen.app_grid_width)
    private val definedHeight =
        windowBackground.resources.getDimensionPixelSize(R.dimen.app_grid_height)
    private val definedMarginHorizontal =
        windowBackground.resources.getDimensionPixelSize(R.dimen.app_grid_margin_horizontal)
    private val definedMarginVertical =
        windowBackground.resources.getDimensionPixelSize(R.dimen.app_grid_margin_vertical)
    private val pageIndicatorSize =
        windowBackground.resources.getDimensionPixelSize(R.dimen.page_indicator_height)
    private val minItemWidth =
        windowBackground.resources.getDimensionPixelSize(R.dimen.car_app_selector_column_min_width)
    private val minItemHeight =
        windowBackground.resources.getDimensionPixelSize(R.dimen.car_app_selector_column_min_height)
    private var lastWidth = 0
    private var lastHeight = 0
    private var gridDimensions: GridDimensions? = null
    private var pageDimensions: PageDimensions? = null

    fun getGridDimensions(): GridDimensions? = gridDimensions

    fun getPageDimensions(): PageDimensions? = pageDimensions

    fun handleWindowSizeChange(
        windowWidth: Int,
        windowHeight: Int,
    ): Boolean {
        var availableWidth = windowWidth
        var availableHeight = windowHeight
        if (useDefinedDimensions) {
            availableWidth = definedWidth
            availableHeight = definedHeight
        }
        if (availableWidth == lastWidth && availableHeight == lastHeight) return false
        lastWidth = availableWidth
        lastHeight = availableHeight

        var gridWidth =
            availableWidth - definedMarginHorizontal * 2 -
                if (isHorizontal()) 0 else pageIndicatorSize
        var gridHeight =
            availableHeight - definedMarginVertical * 2 -
                if (isHorizontal()) pageIndicatorSize else 0
        val columns = (gridWidth / minItemWidth).coerceAtLeast(1)
        val rows = (gridHeight / minItemHeight).coerceAtLeast(1)
        gridWidth = roundDownToModuloMultiple(gridWidth, columns)
        gridHeight = roundDownToModuloMultiple(gridHeight, rows)
        val cellWidth = gridWidth / columns
        val cellHeight = gridHeight / rows
        gridDimensions = GridDimensions(gridWidth, gridHeight, cellWidth, cellHeight, rows, columns)

        val marginHorizontal = (availableWidth - gridWidth) / 2
        val marginVertical = (availableHeight - gridHeight) / 2
        pageDimensions =
            if (isHorizontal()) {
                PageDimensions(
                    availableWidth,
                    gridHeight,
                    marginHorizontal,
                    marginVertical,
                    gridWidth,
                    pageIndicatorSize,
                    availableWidth,
                    availableHeight,
                )
            } else {
                PageDimensions(
                    gridWidth,
                    availableHeight,
                    marginHorizontal,
                    marginVertical,
                    pageIndicatorSize,
                    gridHeight,
                    availableWidth,
                    availableHeight,
                )
            }
        return true
    }

    private fun isHorizontal(): Boolean = AppGridConstants.isHorizontal(pageOrientation)

    private fun roundDownToModuloMultiple(
        input: Int,
        modulo: Int,
    ): Int = input / modulo * modulo

    data class GridDimensions(
        var gridWidthPx: Int,
        var gridHeightPx: Int,
        var cellWidthPx: Int,
        var cellHeightPx: Int,
        var mNumOfRows: Int,
        var mNumOfCols: Int,
    )

    data class PageDimensions(
        var recyclerViewWidthPx: Int,
        var recyclerViewHeightPx: Int,
        var marginHorizontalPx: Int,
        var marginVerticalPx: Int,
        var pageIndicatorWidthPx: Int,
        var pageIndicatorHeightPx: Int,
        var windowWidthPx: Int,
        var windowHeightPx: Int,
    )
}
