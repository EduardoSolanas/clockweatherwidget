package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetClockUpdateModeResolverTest {

    @Test
    fun `missing last render falls back to full update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = null,
            currentEpochMinute = 100L
        )

        assertEquals(WidgetClockUpdateMode.FULL, mode)
    }

    @Test
    fun `exact next minute uses incremental update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 101L
        )

        assertEquals(WidgetClockUpdateMode.INCREMENTAL, mode)
    }

    @Test
    fun `gap of 2 minutes uses incremental update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 102L
        )

        assertEquals(WidgetClockUpdateMode.INCREMENTAL, mode)
    }

    @Test
    fun `gap of 3 minutes uses incremental update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 103L
        )

        assertEquals(WidgetClockUpdateMode.INCREMENTAL, mode)
    }

    @Test
    fun `gap of 4 minutes uses incremental update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 104L
        )

        assertEquals(WidgetClockUpdateMode.INCREMENTAL, mode)
    }

    @Test
    fun `gap of 15 minutes uses incremental update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 115L
        )

        assertEquals(WidgetClockUpdateMode.INCREMENTAL, mode)
    }

    @Test
    fun `gap of 16 minutes falls back to full update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 116L
        )

        assertEquals(WidgetClockUpdateMode.FULL, mode)
    }

    @Test
    fun `duplicate same-minute tick falls back to full update`() {
        val mode = WidgetClockUpdateModeResolver.resolve(
            lastRenderedEpochMinute = 100L,
            currentEpochMinute = 100L
        )

        assertEquals(WidgetClockUpdateMode.FULL, mode)
    }
}
