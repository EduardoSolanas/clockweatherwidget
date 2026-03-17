package com.clockweather.app.presentation.widget.common

enum class WidgetClockUpdateMode {
    FULL,
    INCREMENTAL
}

object WidgetClockUpdateModeResolver {
    fun resolve(lastRenderedEpochMinute: Long?, currentEpochMinute: Long): WidgetClockUpdateMode {
        return if (lastRenderedEpochMinute != null && currentEpochMinute - lastRenderedEpochMinute == 1L) {
            WidgetClockUpdateMode.INCREMENTAL
        } else {
            WidgetClockUpdateMode.FULL
        }
    }
}
