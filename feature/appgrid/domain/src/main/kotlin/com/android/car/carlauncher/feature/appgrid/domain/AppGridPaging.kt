package com.android.car.carlauncher.feature.appgrid.domain

/**
 * Framework-free equivalent of AOSP's PageIndexingHelper. RecyclerView positions are laid out
 * in matrix order while persistence and drag/drop operate on business order. Keeping the mapping
 * here makes RTL, vertical paging and cross-page moves testable without an emulator.
 */
object AppGridPaging {
    fun pageSize(
        columns: Int,
        rows: Int,
    ): Int {
        require(columns > 0 && rows > 0) { "Grid dimensions must be positive." }
        return columns * rows
    }

    /**
     * Returns the RecyclerView item count required to keep every page rectangular.
     *
     * AOSP binds empty view holders for the unused cells on the last page. Keeping those cells in
     * the adapter is important: the grid position/index mapping is page based and otherwise a
     * partial page can address an item past the end of the business list.
     */
    fun pagedItemCount(
        itemCount: Int,
        columns: Int,
        rows: Int,
    ): Int {
        require(itemCount >= 0) { "Item count must be non-negative." }
        val size = pageSize(columns, rows)
        if (itemCount == 0) return 0
        return ((itemCount + size - 1) / size) * size
    }

    fun adapterIndexToGridPosition(
        index: Int,
        columns: Int,
        rows: Int,
        orientation: AppGridOrientation,
        rtl: Boolean,
    ): Int {
        require(index >= 0) { "Adapter index must be non-negative." }
        val size = pageSize(columns, rows)
        if (orientation == AppGridOrientation.VERTICAL) {
            return if (rtl) mirrorColumn(index, columns) else index
        }
        val page = index / size
        val indexOnPage = index % size
        val row = indexOnPage / columns
        var column = indexOnPage % columns
        if (rtl) column = columns - column - 1
        return page * size + column * rows + row
    }

    fun gridPositionToAdapterIndex(
        position: Int,
        columns: Int,
        rows: Int,
        orientation: AppGridOrientation,
        rtl: Boolean,
    ): Int {
        require(position >= 0) { "Grid position must be non-negative." }
        val size = pageSize(columns, rows)
        if (orientation == AppGridOrientation.VERTICAL) {
            return if (rtl) mirrorColumn(position, columns) else position
        }
        val page = position / size
        val positionOnPage = position % size
        var column = positionOnPage / rows
        val row = positionOnPage % rows
        if (rtl) column = columns - column - 1
        return page * size + row * columns + column
    }

    fun pageOfAdapterIndex(
        index: Int,
        columns: Int,
        rows: Int,
    ): Int = index / pageSize(columns, rows)

    private fun mirrorColumn(
        position: Int,
        columns: Int,
    ): Int {
        val column = position % columns
        return position - column + columns - column - 1
    }
}
