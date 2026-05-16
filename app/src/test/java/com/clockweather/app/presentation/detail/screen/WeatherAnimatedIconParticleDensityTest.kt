package com.clockweather.app.presentation.detail.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAnimatedIconParticleDensityTest {

    @Test
    fun `rain particle density stays sparse across intensities`() {
        assertEquals(14, rainParticleCountForIntensity(0.35f))
        assertEquals(28, rainParticleCountForIntensity(0.8f))
        assertEquals(46, rainParticleCountForIntensity(1.3f))
        assertEquals(64, rainParticleCountForIntensity(1.7f))
        assertEquals(82, rainParticleCountForIntensity(2.2f))
    }

    @Test
    fun `snow particle density stays sparse across intensities`() {
        assertEquals(18, snowParticleCountForIntensity(0.55f))
        assertEquals(32, snowParticleCountForIntensity(0.95f))
        assertEquals(48, snowParticleCountForIntensity(1.45f))
        assertEquals(64, snowParticleCountForIntensity(1.8f))
    }

    @Test
    fun `drizzle particle density is lower than slight rain`() {
        assertTrue(drizzleParticleCountForIntensity(1f) < rainParticleCountForIntensity(0.35f))
    }
}
