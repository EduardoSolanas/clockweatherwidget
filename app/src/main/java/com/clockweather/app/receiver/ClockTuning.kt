package com.clockweather.app.receiver

/**
 * Single source of truth for all time-sync receiver tunables.
 * Centralising these makes tradeoffs explicit and diffs reviewable.
 */
object ClockTuning {
    /** How long to wait for ACTION_TIME_TICK before the alarm backup takes over (ms). */
    const val TIME_TICK_GRACE_MS = 1200L

    /** Alarms fired within this many ms before the minute boundary get extra wait logic. */
    const val EARLY_ALARM_WINDOW_START_MS = 58_000L

    /** Poll interval while waiting for the clock to tick over the minute boundary (ms). */
    const val EARLY_ALARM_POLL_MS = 250L

    /** Max extra time to wait for minute boundary to tick over when alarm fires early (ms). */
    const val EARLY_ALARM_MAX_EXTRA_WAIT_MS = 2000L

    /** Max time to wait for a late TIME_TICK before treating this minute as tick-missed (ms). */
    const val LATE_TIME_TICK_MAX_WAIT_MS = 2500L

    /** Poll interval while waiting for TIME_TICK to arrive (ms). */
    const val LATE_TIME_TICK_POLL_MS = 100L

    /** Keepalive alarm interval while screen is off (ms). */
    const val KEEPALIVE_INTERVAL_MS = 60 * 1000L

    /** Throttle window for unlock-convergence coroutine (prevents rapid SCREEN_ON bursts). */
    const val UNLOCK_CONVERGENCE_THROTTLE_MS = 2_500L
}
