package com.clockweather.app.presentation.widget.common

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Captures wall-clock display time and epoch-minute from the same instant.
 * This avoids minute-boundary races where one value is sampled before the
 * boundary and the other after.
 */
data class ClockSnapshot(
    val localTime: LocalTime,
    val epochMinute: Long
) {
    companion object {
        fun now(
            nowMillis: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): ClockSnapshot {
            return ClockSnapshot(
                localTime = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalTime(),
                epochMinute = nowMillis / 60000L
            )
        }
    }
}
