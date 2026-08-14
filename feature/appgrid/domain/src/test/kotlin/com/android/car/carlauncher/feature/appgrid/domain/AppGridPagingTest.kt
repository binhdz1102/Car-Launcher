package com.android.car.carlauncher.feature.appgrid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGridPagingTest {
    @Test
    fun horizontalMappingRoundTripsInLtrAndRtl() {
        listOf(false, true).forEach { rtl ->
            (0 until 24).forEach { index ->
                val grid = AppGridPaging.adapterIndexToGridPosition(index, 5, 4, AppGridOrientation.HORIZONTAL, rtl)
                assertEquals(
                    index,
                    AppGridPaging.gridPositionToAdapterIndex(grid, 5, 4, AppGridOrientation.HORIZONTAL, rtl),
                )
            }
        }
    }

    @Test
    fun verticalRtlMirrorsColumns() {
        assertEquals(4, AppGridPaging.adapterIndexToGridPosition(0, 5, 4, AppGridOrientation.VERTICAL, true))
        assertEquals(0, AppGridPaging.gridPositionToAdapterIndex(4, 5, 4, AppGridOrientation.VERTICAL, true))
        assertTrue(AppGridPaging.pageOfAdapterIndex(20, 5, 4) == 1)
    }
}
