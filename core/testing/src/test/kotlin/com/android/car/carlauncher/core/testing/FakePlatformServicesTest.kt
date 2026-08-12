package com.android.car.carlauncher.core.testing

import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.platform.UxrState
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePlatformServicesTest {
    @Test
    fun drivingRestrictionFakePublishesTheMostRecentUxState() {
        val monitor = FakeDrivingRestrictionMonitor()
        val parked =
            UxrState(
                level = DrivingRestriction.UNRESTRICTED,
                requiresDistractionOptimization = false,
                noKeyboard = false,
                serviceAvailable = true,
            )

        monitor.set(parked)

        assertEquals(parked, monitor.restrictions.value)
    }
}
