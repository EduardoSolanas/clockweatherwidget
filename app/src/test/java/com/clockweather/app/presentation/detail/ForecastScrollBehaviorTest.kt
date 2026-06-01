package com.clockweather.app.presentation.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastScrollBehaviorTest {

    @Test
    fun `7 forecasts are not scrollable`() {
        assertFalse(isScrollable(forecastCount = 7))
    }

    @Test
    fun `fewer than 7 forecasts are not scrollable`() {
        assertFalse(isScrollable(forecastCount = 3))
        assertFalse(isScrollable(forecastCount = 0))
    }

    @Test
    fun `14 forecasts are scrollable`() {
        assertTrue(isScrollable(forecastCount = 14))
    }

    @Test
    fun `8 forecasts are scrollable`() {
        assertTrue(isScrollable(forecastCount = 8))
    }

    private fun isScrollable(forecastCount: Int): Boolean = forecastCount > 7
}

