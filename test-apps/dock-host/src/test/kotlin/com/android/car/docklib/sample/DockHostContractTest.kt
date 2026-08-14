package com.android.car.docklib.sample

import org.junit.Assert.assertTrue
import org.junit.Test

class DockHostContractTest {
    @Test
    fun sampleHostIsDedicatedPackage() {
        assertTrue(DockHostActivity::class.java.name.contains("docklib.sample"))
    }
}
