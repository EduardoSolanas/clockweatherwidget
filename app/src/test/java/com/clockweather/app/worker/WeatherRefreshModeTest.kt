package com.clockweather.app.worker

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherRefreshModeTest {

    @Test
    fun `user refresh work forces a provider refresh`() {
        val input = Data.Builder()
            .putBoolean(WeatherUpdateWorker.INPUT_FORCE_REFRESH, true)
            .build()

        assertEquals(WeatherRefreshMode.FORCE, weatherRefreshMode(input))
    }

    @Test
    fun `ordinary work remains freshness gated`() {
        assertEquals(WeatherRefreshMode.ENSURE_FRESH, weatherRefreshMode(Data.EMPTY))
    }
}
