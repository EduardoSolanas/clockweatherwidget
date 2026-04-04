package com.clockweather.app.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD: smartDefaultForecastDays() must return 14 for wide screens (>=600dp) and 7 for phones.
 *
 * RED → fails until SettingsViewModel.Companion.smartDefaultForecastDays() is extracted.
 */
class ForecastDaysDefaultTest {

    @Test
    fun `returns 14 for tablet width 600dp`() {
        assertEquals(14, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 600))
    }

    @Test
    fun `returns 14 for large tablet 840dp`() {
        assertEquals(14, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 840))
    }

    @Test
    fun `returns 7 for phone width 599dp`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 599))
    }

    @Test
    fun `returns 7 for typical phone width 360dp`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 360))
    }

    @Test
    fun `returns 7 for small phone width 320dp`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 320))
    }

    @Test
    fun `boundary 600dp is wide - returns 14`() {
        assertEquals(14, SettingsViewModel.smartDefaultForecastDays(600))
    }

    @Test
    fun `boundary 599dp is narrow - returns 7`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(599))
    }
}

