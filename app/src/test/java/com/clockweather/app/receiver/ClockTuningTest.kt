package com.clockweather.app.receiver

import org.junit.Assert.*
import org.junit.Test

/**
 * Contract tests for [ClockTuning] — the single-source-of-truth for all receiver tunables.
 *
 * Ensures constants are present with sane values so changes to them are visible
 * as explicit code diffs rather than magic numbers scattered across receivers.
 */
class ClockTuningTest {

    @Test
    fun `TIME_TICK_GRACE_MS is positive and less than one minute`() {
        assertTrue(ClockTuning.TIME_TICK_GRACE_MS > 0)
        assertTrue(ClockTuning.TIME_TICK_GRACE_MS < 60_000)
    }

    @Test
    fun `EARLY_ALARM_WINDOW_START_MS is near the end of a minute`() {
        // Should fire near minute boundary — within last 10 seconds
        assertTrue(ClockTuning.EARLY_ALARM_WINDOW_START_MS >= 50_000)
        assertTrue(ClockTuning.EARLY_ALARM_WINDOW_START_MS < 60_000)
    }

    @Test
    fun `EARLY_ALARM_MAX_EXTRA_WAIT_MS is positive and less than 10 seconds`() {
        assertTrue(ClockTuning.EARLY_ALARM_MAX_EXTRA_WAIT_MS > 0)
        assertTrue(ClockTuning.EARLY_ALARM_MAX_EXTRA_WAIT_MS <= 10_000)
    }

    @Test
    fun `LATE_TIME_TICK_MAX_WAIT_MS is positive and less than one minute`() {
        assertTrue(ClockTuning.LATE_TIME_TICK_MAX_WAIT_MS > 0)
        assertTrue(ClockTuning.LATE_TIME_TICK_MAX_WAIT_MS < 60_000)
    }

    @Test
    fun `KEEPALIVE_INTERVAL_MS equals 60 seconds`() {
        assertEquals(60_000L, ClockTuning.KEEPALIVE_INTERVAL_MS)
    }

    @Test
    fun `UNLOCK_CONVERGENCE_THROTTLE_MS is positive`() {
        assertTrue(ClockTuning.UNLOCK_CONVERGENCE_THROTTLE_MS > 0)
    }
}
