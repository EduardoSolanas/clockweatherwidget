package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class ClockSnapshotTest {

    @Test
    fun `snapshot keeps local minute and epoch minute aligned for same instant`() {
        val zone = ZoneId.of("UTC")
        val millis = (13L * 60_000L) + 59_999L // 00:13:59.999 UTC

        val snapshot = ClockSnapshot.now(nowMillis = millis, zoneId = zone)

        assertEquals(13, snapshot.localTime.minute)
        assertEquals(13L, snapshot.epochMinute % 60L)
    }

    @Test
    fun `snapshot advances both local minute and epoch minute at boundary`() {
        val zone = ZoneId.of("UTC")
        val millis = 14L * 60_000L // 00:14:00.000 UTC

        val snapshot = ClockSnapshot.now(nowMillis = millis, zoneId = zone)

        assertEquals(14, snapshot.localTime.minute)
        assertEquals(14L, snapshot.epochMinute % 60L)
    }
}
