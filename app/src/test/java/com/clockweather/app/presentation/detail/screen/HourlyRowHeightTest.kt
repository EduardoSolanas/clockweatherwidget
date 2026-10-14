package com.clockweather.app.presentation.detail.screen

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The hourly graph rows hold text that scales with the user's font size next to padding and
 * icons that do not. A fixed row height clipped the "12:00" label at font scale 1.25.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HourlyRowHeightTest {

    private fun density(fontScale: Float): Density {
        RuntimeEnvironment.setFontScale(fontScale)
        return Density(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `time row keeps its design height at the default font size`() {
        assertEquals(50f, density(1f).hourlyTimeRowHeight().value, 0.01f)
    }

    @Test
    fun `time row grows with the text so the clock label is not clipped`() {
        // 18dp padding + two 16sp lines: 18 + 32 * 1.25 = 58dp
        assertEquals(58f, density(1.25f).hourlyTimeRowHeight().value, 0.01f)
        assertEquals(82f, density(2f).hourlyTimeRowHeight().value, 0.01f)
    }

    @Test
    fun `meta row keeps its design height until the text outgrows it`() {
        assertEquals(144f, density(1f).hourlyMetaSliceHeight().value, 0.01f)
        assertEquals(144f, density(1.25f).hourlyMetaSliceHeight().value, 0.01f)
        // 88dp of icons and padding + two 16sp lines: 88 + 32 * 2 = 152dp
        assertEquals(152f, density(2f).hourlyMetaSliceHeight().value, 0.01f)
    }
}
