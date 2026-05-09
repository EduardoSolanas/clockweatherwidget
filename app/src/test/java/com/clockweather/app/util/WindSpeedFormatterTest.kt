package com.clockweather.app.util

import com.clockweather.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WindSpeedFormatterTest {

    @Test
    fun `convert kmh to kmh returns same value`() {
        assertEquals(10.0, WindSpeedFormatter.convert(10.0, SpeedUnit.KMH), 0.01)
    }

    @Test
    fun `convert kmh to mph`() {
        assertEquals(6.0, WindSpeedFormatter.convert(10.0, SpeedUnit.MPH), 0.5)
    }

    @Test
    fun `convert kmh to ms`() {
        assertEquals(2.78, WindSpeedFormatter.convert(10.0, SpeedUnit.MS), 0.01)
    }

    @Test
    fun `convert kmh to knots`() {
        assertEquals(5.4, WindSpeedFormatter.convert(10.0, SpeedUnit.KNOTS), 0.1)
    }

    @Test
    fun `format returns rounded integer string`() {
        assertEquals("6", WindSpeedFormatter.format(10.0, SpeedUnit.MPH))
    }

    @Test
    fun `formatWithUnit appends unit symbol`() {
        assertEquals("10km/h", WindSpeedFormatter.formatWithUnit(10.0, SpeedUnit.KMH))
        assertEquals("6mph", WindSpeedFormatter.formatWithUnit(10.0, SpeedUnit.MPH))
    }
}
