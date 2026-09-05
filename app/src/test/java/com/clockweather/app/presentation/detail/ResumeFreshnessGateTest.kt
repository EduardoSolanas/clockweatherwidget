package com.clockweather.app.presentation.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queue step "resume freshness" (finding 4) of docs/TIME_WEATHER_SYNC_REVIEW.md.
 *
 * Returning to the screen after a long absence has to re-check whether the cache is still
 * current. The first ON_RESUME arrives during the same composition that constructed the
 * ViewModel, whose init already issued that check, so honouring it would download twice on
 * every cold start.
 */
class ResumeFreshnessGateTest {

    @Test
    fun `the resume that accompanies construction does not check again`() {
        val gate = ResumeFreshnessGate()

        assertFalse(
            "init already issued the first freshness check",
            gate.shouldCheckOnResume()
        )
    }

    @Test
    fun `returning to the screen checks freshness`() {
        val gate = ResumeFreshnessGate()
        gate.shouldCheckOnResume()

        assertTrue(gate.shouldCheckOnResume())
    }

    @Test
    fun `every later return checks again`() {
        val gate = ResumeFreshnessGate()
        gate.shouldCheckOnResume()

        assertTrue(gate.shouldCheckOnResume())
        assertTrue(gate.shouldCheckOnResume())
        assertTrue(gate.shouldCheckOnResume())
    }
}
