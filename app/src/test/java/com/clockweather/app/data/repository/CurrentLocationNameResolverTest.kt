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
    fun `uses city district when available`() {
        val resolved = resolveCurrentLocationName(
            locality = null,
            cityDistrict = "東京都新宿区",
            subAdminArea = "東京都",
            adminArea = "関東地方",
            fallbackLabel = "Current Location"
        )
        assertEquals("東京都新宿区", resolved)
    }

    @Test
    fun `uses sub locality when available`() {
        val resolved = resolveCurrentLocationName(
            locality = "Ciudad de México",
            subLocality = "Centro",
            subAdminArea = "Ciudad de México",
            adminArea = "México",
            fallbackLabel = "Current Location"
        )
        assertEquals("Centro", resolved)
    }

    @Test
    fun `falls back to sub admin area when no specific fields are available`() {
        val resolved = resolveCurrentLocationName(
            locality = null,
            subAdminArea = "Île-de-France",
            adminArea = "France",
            fallbackLabel = "Current Location"
        )
        assertEquals("Île-de-France", resolved)
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

