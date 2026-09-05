package com.clockweather.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding 8 of docs/TIME_WEATHER_SYNC_REVIEW.md.
 *
 * A last-known fix is accepted on `now - fix.time < maxAge`, computed from the wall clock.
 * That subtraction has no lower bound, so a fix stamped in the future — a clock rolled back,
 * a timezone or NTP correction, a device whose clock was wrong when the fix was recorded —
 * yields a negative age, which is smaller than every threshold. An arbitrarily wrong fix then
 * reads as the freshest possible one.
 */
class LastKnownFixAgeTest {

    private val fifteenMinutes = 15 * 60 * 1000L

    @Test
    fun `a recent fix is acceptable`() {
        assertTrue(isLastKnownFixAcceptable(nowMs = 10_000_000L, fixTimeMs = 10_000_000L - 60_000L, maxAgeMs = fifteenMinutes))
    }

    @Test
    fun `a fix older than the threshold is rejected`() {
        assertFalse(isLastKnownFixAcceptable(nowMs = 10_000_000L, fixTimeMs = 10_000_000L - fifteenMinutes - 1, maxAgeMs = fifteenMinutes))
    }

    @Test
    fun `a fix stamped in the future is rejected rather than treated as brand new`() {
        assertFalse(
            "a negative age is smaller than any threshold, so an unbounded check accepts it",
            isLastKnownFixAcceptable(nowMs = 10_000_000L, fixTimeMs = 10_000_000L + 10 * 60_000L, maxAgeMs = fifteenMinutes)
        )
    }

    /** Clocks disagree by a minute or so routinely; only implausible futures are rejected. */
    @Test
    fun `trivial clock skew is tolerated`() {
        assertTrue(
            isLastKnownFixAcceptable(nowMs = 10_000_000L, fixTimeMs = 10_000_000L + 500L, maxAgeMs = fifteenMinutes)
        )
    }
}
