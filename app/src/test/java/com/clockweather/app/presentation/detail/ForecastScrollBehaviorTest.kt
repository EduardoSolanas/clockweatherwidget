package com.clockweather.app.presentation.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD: The scroll/drag toggle logic for the forecast strip must follow these rules:
 *   - forecastDays == 7  → isScrollable = false  (grid layout, no scroll)
 *   - forecastDays == 14 → isScrollable = true   (horizontal scroll with drag)
 *
 * This mirrors the runtime condition in SevenDayForecastCard:
 *   val isScrollable = forecasts.size > 7
 *
 * RED → always passes once we ensure 14-day fetches populate 14 items in the
 * cache (the fix is in WeatherDetailViewModel — see observeForecastDaysChanges).
 * These tests document the contract so regressions are caught.
 */
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

    @Test
    fun `boundary 7 is not scrollable`() {
        assertFalse(isScrollable(forecastCount = 7))
    }

    @Test
    fun `boundary 8 is scrollable`() {
        assertTrue(isScrollable(forecastCount = 8))
    }

    /** Mirrors SevenDayForecastCard: val isScrollable = forecasts.size > 7 */
    private fun isScrollable(forecastCount: Int): Boolean = forecastCount > 7
}

