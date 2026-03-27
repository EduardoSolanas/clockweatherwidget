package com.clockweather.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentLocationNameResolverTest {

    @Test
    fun `uses locality when available`() {
        val resolved = resolveCurrentLocationName(
            locality = "Manchester",
            subAdminArea = "Greater Manchester",
            adminArea = "England",
            fallbackLabel = "Current Location"
        )
        assertEquals("Manchester", resolved)
    }

    @Test
    fun `falls back to sub admin area when locality missing`() {
        val resolved = resolveCurrentLocationName(
            locality = null,
            subAdminArea = "Greater London",
            adminArea = "England",
            fallbackLabel = "Current Location"
        )
        assertEquals("Greater London", resolved)
    }

    @Test
    fun `falls back to admin area when locality and sub admin area missing`() {
        val resolved = resolveCurrentLocationName(
            locality = null,
            subAdminArea = null,
            adminArea = "California",
            fallbackLabel = "Current Location"
        )
        assertEquals("California", resolved)
    }

    @Test
    fun `uses fallback label when no reverse geo name is available`() {
        val resolved = resolveCurrentLocationName(
            locality = null,
            subAdminArea = null,
            adminArea = null,
            fallbackLabel = "Current Location"
        )
        assertEquals("Current Location", resolved)
    }
}

