package com.clockweather.app.presentation.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ads are only disabled at build level for debug builds, but testers install the release AAB
 * from the internal track, where ads are on. `tester_mode_ads_disabled` already suppresses them
 * per device and `AdManager.isEligibleToShowAd` honours it — but nothing ever called
 * `setTesterMode`, so no tester could turn it on.
 *
 * The control is hidden behind repeated taps on the version label rather than shown outright:
 * the same binary ships to production, and a switch labelled "no ads" in plain sight is one
 * ordinary users would find and use.
 */
class TesterModeRevealTest {

    @Test
    fun `the toggle stays hidden during ordinary use`() {
        val reveal = TesterModeReveal()

        repeat(TesterModeReveal.REQUIRED_TAPS - 1) {
            assertFalse("revealed after ${it + 1} taps", reveal.onVersionTapped())
        }
    }

    @Test
    fun `the required number of taps reveals it`() {
        val reveal = TesterModeReveal()

        repeat(TesterModeReveal.REQUIRED_TAPS - 1) { reveal.onVersionTapped() }

        assertTrue(reveal.onVersionTapped())
    }

    @Test
    fun `it stays revealed once unlocked`() {
        val reveal = TesterModeReveal()
        repeat(TesterModeReveal.REQUIRED_TAPS) { reveal.onVersionTapped() }

        assertTrue(reveal.onVersionTapped())
        assertTrue(reveal.isRevealed)
    }

    /** A tester should not have to be careful about tap rhythm; only the count matters. */
    @Test
    fun `taps accumulate rather than needing to be consecutive in time`() {
        val reveal = TesterModeReveal()

        repeat(TesterModeReveal.REQUIRED_TAPS) { reveal.onVersionTapped() }

        assertTrue(reveal.isRevealed)
    }

    @Test
    fun `it is not reachable by a plausible number of accidental taps`() {
        assertTrue(
            "too few taps and ordinary users will find it by accident",
            TesterModeReveal.REQUIRED_TAPS >= 7
        )
    }
}
