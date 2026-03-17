package com.clockweather.app.presentation.widget.common

enum class WidgetClockUpdateMode {
    FULL,
    INCREMENTAL
}

object WidgetClockUpdateModeResolver {
    /**
     * Returns INCREMENTAL if the gap since the last render is 1–3 minutes.
     * Small gaps (2–3 min) are common during Doze and don't warrant an
     * expensive full rebuild — the clock digits are computed from [LocalTime.now()]
     * regardless of gap size.
     */
    fun resolve(lastRenderedEpochMinute: Long?, currentEpochMinute: Long): WidgetClockUpdateMode {
        return if (lastRenderedEpochMinute != null && currentEpochMinute - lastRenderedEpochMinute in 1L..3L) {
            WidgetClockUpdateMode.INCREMENTAL
        } else {
            WidgetClockUpdateMode.FULL
        }
    }
}
