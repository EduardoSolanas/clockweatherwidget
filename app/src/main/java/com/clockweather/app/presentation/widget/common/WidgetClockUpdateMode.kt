package com.clockweather.app.presentation.widget.common

enum class WidgetClockUpdateMode {
    FULL,
    INCREMENTAL
}

object WidgetClockUpdateModeResolver {
    /**
     * Returns INCREMENTAL if the gap since the last render is 1–15 minutes.
     * Small-to-medium gaps are common during Doze and don't warrant an
     * expensive full rebuild — the clock digits are computed from [LocalTime.now()]
     * regardless of gap size. Gaps > 15 min suggest a significant state change
     * (timezone, prolonged sleep, etc.) where a full rebuild is appropriate.
     */
    fun resolve(lastRenderedEpochMinute: Long?, currentEpochMinute: Long): WidgetClockUpdateMode {
        return if (lastRenderedEpochMinute != null && currentEpochMinute - lastRenderedEpochMinute in 1L..15L) {
            WidgetClockUpdateMode.INCREMENTAL
        } else {
            WidgetClockUpdateMode.FULL
        }
    }
}
