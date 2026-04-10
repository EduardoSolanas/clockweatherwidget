package com.clockweather.app.presentation.detail.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CurrentWeatherCardTest {

    @Test
    fun `resolveForecastIsToday returns true when forecast date matches today`() {
        val today = LocalDate.of(2026, 4, 10)
        assertTrue(resolveForecastIsToday(today, today))
    }

    @Test
    fun `resolveForecastIsToday returns false for yesterday`() {
        val today = LocalDate.of(2026, 4, 10)
        assertFalse(resolveForecastIsToday(today.minusDays(1), today))
    }

    @Test
    fun `resolveForecastIsToday returns false for tomorrow`() {
        val today = LocalDate.of(2026, 4, 10)
        assertFalse(resolveForecastIsToday(today.plusDays(1), today))
    }

    @Test
    fun `resolveForecastIsToday catches stale data bug - index 0 was yesterday`() {
        // Core regression: isToday = index == 0 would return true here, but
        // resolveForecastIsToday correctly returns false when first forecast is stale
        val today = LocalDate.of(2026, 4, 10)
        val staleFirstForecastDate = today.minusDays(1)  // yesterday's cached data
        assertFalse(resolveForecastIsToday(staleFirstForecastDate, today))
    }
}
