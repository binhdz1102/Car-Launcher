package com.android.car.carlauncher.fixture

import org.junit.Assert.assertEquals
import org.junit.Test

class FixtureContractTest {
    @Test
    fun mediaActionsStayInsideTheFixturePackage() {
        assertEquals(
            "${FixtureContract.PACKAGE_NAME}.action.MEDIA_PLAY",
            FixtureContract.ACTION_MEDIA_PLAY,
        )
    }
}
