package com.clockweather.app.presentation.detail.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD: The scrollable 14-day forecast row must use exactly the same
 * column width as the 7-day grid so the appearance is consistent.
 *
 * The 7-day grid distributes available width equally:
 *   columnWidth = (availableWidth - spacing * (itemsVisible - 1)) / itemsVisible
 *
 * The 14-day scrollable row must use the same formula for its fixed
 * per-column width so columns look identical when toggling the setting.
 *
 * RED → fails until forecastColumnWidth() is extracted from SevenDayForecastCard.
 */
class ForecastColumnWidthTest {

    @Test
    fun `column width on 380dp available with 4dp spacing and 7 items`() {
        // (380 - 4*6) / 7 = 356/7 ≈ 50.857
        val expected = (380f - 4f * 6f) / 7f
        assertEquals(expected, forecastColumnWidth(availableWidthDp = 380f), 0.01f)
    }

    @Test
    fun `column width on 412dp available (Pixel 9 Pro, 16dp h-padding each side)`() {
        // content width = 412 - 32 = 380dp  → same as above
        val contentWidth = 412f - 32f
        val expected = (contentWidth - 4f * 6f) / 7f
        assertEquals(expected, forecastColumnWidth(availableWidthDp = contentWidth), 0.01f)
    }

    @Test
    fun `column width on 360dp phone`() {
        val contentWidth = 360f - 32f  // 328dp
        val expected = (contentWidth - 4f * 6f) / 7f
        assertEquals(expected, forecastColumnWidth(availableWidthDp = contentWidth), 0.01f)
    }

    @Test
    fun `scroll step equals 7 columns worth of width`() {
        val columnWidth = 50.86f
        val spacing = 4f
        val expected = (columnWidth + spacing) * 7f
        assertEquals(expected, forecastScrollStep(columnWidthDp = columnWidth), 0.01f)
    }

    @Test
    fun `scroll step always includes spacing so no partial column shows`() {
        val columnWidth = 44f
        val step = forecastScrollStep(columnWidthDp = columnWidth)
        // step must be a multiple of (columnWidth + spacing)
        val remainder = step % (columnWidth + 4f)
        assertEquals(0f, remainder, 0.01f)
    }
}

