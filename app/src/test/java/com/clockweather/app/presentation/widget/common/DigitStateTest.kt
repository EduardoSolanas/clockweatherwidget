package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [DigitState.from] — the digit computation that drives both
 * [ClockWeatherApplication.pushClockInstant] and [WidgetDataBinder.bindClockViews].
 *
 * A regression here means wrong digits on every clock update path.
 */
class DigitStateTest {

    // ── 24h mode ──────────────────────────────────────────────

    @Test
    fun `24h midnight is 00 colon 00`() {
        val ds = DigitState.from(hour = 0, minute = 0, is24h = true)
        assertEquals(DigitState(0, 0, 0, 0), ds)
    }

    @Test
    fun `24h 09 colon 05`() {
        val ds = DigitState.from(hour = 9, minute = 5, is24h = true)
        assertEquals(DigitState(0, 9, 0, 5), ds)
    }

    @Test
    fun `24h 14 colon 37`() {
        val ds = DigitState.from(hour = 14, minute = 37, is24h = true)
        assertEquals(DigitState(1, 4, 3, 7), ds)
    }

    @Test
    fun `24h 23 colon 59`() {
        val ds = DigitState.from(hour = 23, minute = 59, is24h = true)
        assertEquals(DigitState(2, 3, 5, 9), ds)
    }

    // ── 12h mode ──────────────────────────────────────────────

    @Test
    fun `12h midnight (hour 0) shows 12 colon 00`() {
        val ds = DigitState.from(hour = 0, minute = 0, is24h = false)
        assertEquals(DigitState(1, 2, 0, 0), ds)
    }

    @Test
    fun `12h 1am shows 01 colon 00`() {
        val ds = DigitState.from(hour = 1, minute = 0, is24h = false)
        assertEquals(DigitState(0, 1, 0, 0), ds)
    }

    @Test
    fun `12h noon (hour 12) shows 12 colon 00`() {
        val ds = DigitState.from(hour = 12, minute = 0, is24h = false)
        assertEquals(DigitState(1, 2, 0, 0), ds)
    }

    @Test
    fun `12h 1pm (hour 13) shows 01 colon 00`() {
        val ds = DigitState.from(hour = 13, minute = 0, is24h = false)
        assertEquals(DigitState(0, 1, 0, 0), ds)
    }

    @Test
    fun `12h 11pm (hour 23) shows 11 colon 59`() {
        val ds = DigitState.from(hour = 23, minute = 59, is24h = false)
        assertEquals(DigitState(1, 1, 5, 9), ds)
    }

    @Test
    fun `12h 12pm (hour 12) minute 30 shows 12 colon 30`() {
        val ds = DigitState.from(hour = 12, minute = 30, is24h = false)
        assertEquals(DigitState(1, 2, 3, 0), ds)
    }
}
