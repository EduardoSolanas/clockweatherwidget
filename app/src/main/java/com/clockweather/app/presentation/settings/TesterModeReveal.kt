package com.clockweather.app.presentation.settings

/**
 * Counts taps on the version label to reveal the tester switch.
 *
 * Testers install the release build from the internal track, where `ADS_ENABLED` is true, so
 * the only per-device way to suppress ads is `tester_mode_ads_disabled`. That switch cannot
 * simply be shown: the same binary ships to production, and an openly labelled "no ads" control
 * is one ordinary users would find and use.
 *
 * Deliberately counts taps rather than timing them — a tester should not have to get a rhythm
 * right — and stays revealed once unlocked, so a mistap does not send them back to the start.
 */
class TesterModeReveal {

    var isRevealed: Boolean = false
        private set

    private var taps = 0

    /** @return whether the switch should now be shown. */
    fun onVersionTapped(): Boolean {
        if (isRevealed) return true
        taps++
        if (taps >= REQUIRED_TAPS) isRevealed = true
        return isRevealed
    }

    companion object {
        /** High enough that ordinary use will not stumble into it. */
        const val REQUIRED_TAPS = 7
    }
}
