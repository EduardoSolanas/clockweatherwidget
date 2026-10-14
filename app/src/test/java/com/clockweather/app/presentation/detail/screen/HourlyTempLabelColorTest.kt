package com.clockweather.app.presentation.detail.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The 24h graph sits on a card that is light in the light theme, so white labels vanished. */
class HourlyTempLabelColorTest {

    private val lightThemeText = Color(0xFF1A1C1E)
    private val darkThemeText = Color(0xFFE3E2E6)

    @Test
    fun `other hours use the theme text colour so they stay readable on a light card`() {
        assertEquals(lightThemeText, hourlyTempLabelColor(isCurrent = false, onSurface = lightThemeText))
        assertNotEquals(Color.White, hourlyTempLabelColor(isCurrent = false, onSurface = lightThemeText))
    }

    @Test
    fun `other hours stay readable on a dark card too`() {
        assertEquals(darkThemeText, hourlyTempLabelColor(isCurrent = false, onSurface = darkThemeText))
    }

    @Test
    fun `the current hour keeps the curve colour in either theme`() {
        assertEquals(CurveColor, hourlyTempLabelColor(isCurrent = true, onSurface = lightThemeText))
        assertEquals(CurveColor, hourlyTempLabelColor(isCurrent = true, onSurface = darkThemeText))
    }
}
