package com.clockweather.app.presentation.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionRefreshGateTest {

    @Test
    fun `initial granted permission belongs to startup freshness owner`() {
        val gate = LocationPermissionRefreshGate()

        assertFalse(gate.shouldRefreshForPermissionChange(granted = true))
    }

    @Test
    fun `grant after denial requests a refresh`() {
        val gate = LocationPermissionRefreshGate()

        assertFalse(gate.shouldRefreshForPermissionChange(granted = false))
        assertTrue(gate.shouldRefreshForPermissionChange(granted = true))
    }
}
