package com.android.car.carlauncher.pagination

import android.view.View
import com.android.car.carlauncher.AppGridConstants

/** Deterministic page/index conversion shared by paging, drag and rotary focus. */
class PageIndexingHelper(
    private val numOfCols: Int,
    private val numOfRows: Int,
    private val pageOrientation: Int,
) {
    private var layoutDirection: Int = View.LAYOUT_DIRECTION_LTR
    private val pageSize: Int
        get() = numOfCols * numOfRows

    fun setLayoutDirection(layoutDirection: Int) {
        this.layoutDirection = layoutDirection
    }

    fun getOffsetBoundDirection(gridPosition: Int): Int {
        if (AppGridConstants.isHorizontal(pageOrientation)) {
            val column = (gridPosition / numOfRows) % numOfCols
            return when (column) {
                0 -> AppGridConstants.AppItemBoundDirection.LEFT
                numOfCols - 1 -> AppGridConstants.AppItemBoundDirection.RIGHT
                else -> AppGridConstants.AppItemBoundDirection.NONE
            }
        }
        val row = (gridPosition / numOfCols) % numOfRows
        return when (row) {
            0 -> AppGridConstants.AppItemBoundDirection.TOP
            numOfRows - 1 -> AppGridConstants.AppItemBoundDirection.BOTTOM
            else -> AppGridConstants.AppItemBoundDirection.NONE
        }
    }

    fun gridPositionToAdaptorIndex(position: Int): Int {
        if (!AppGridConstants.isHorizontal(pageOrientation)) {
            return if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                val column = position % numOfCols
                position - column + numOfCols - column - 1
            } else {
                position
            }
        }
        val block = pageSize
        val positionOnPage = position % block
        val page = position / block
        val row = positionOnPage % numOfRows
        var column = positionOnPage / numOfRows
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) column = numOfCols - column - 1
        return page * block + row * numOfCols + column
    }

    fun adaptorIndexToGridPosition(index: Int): Int {
        if (!AppGridConstants.isHorizontal(pageOrientation)) {
            return if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                val column = index % numOfCols
                index - column + numOfCols - column - 1
            } else {
                index
            }
        }
        val block = pageSize
        val indexOnPage = index % block
        val page = index / block
        val row = indexOnPage / numOfCols
        var column = indexOnPage % numOfCols
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) column = numOfCols - column - 1
        return page * block + column * numOfRows + row
    }

    fun roundToFirstIndexOnPage(gridPosition: Int): Int = gridPosition / pageSize * pageSize

    fun roundToLastIndexOnPage(gridPosition: Int): Int = (gridPosition / pageSize + 1) * pageSize - 1
}
