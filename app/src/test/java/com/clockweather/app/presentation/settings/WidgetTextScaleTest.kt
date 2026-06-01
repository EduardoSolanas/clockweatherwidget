package com.clockweather.app.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTextScaleTest {

    @Test
    fun `widget text scale defaults to normal`() {
        assertEquals(1f, SettingsViewModel.normalizeWidgetTextScale(null), 0.001f)
    }

    @Test
    fun `widget text scale does not shrink below normal`() {
        assertEquals(1f, SettingsViewModel.normalizeWidgetTextScale(0.75f), 0.001f)
    }

    @Test
    fun `widget text scale allows five percent increase`() {
        assertEquals(1.05f, SettingsViewModel.normalizeWidgetTextScale(1.05f), 0.001f)
    }

    @Test
    fun `widget text scale caps above five percent increase`() {
        assertEquals(1.05f, SettingsViewModel.normalizeWidgetTextScale(1.15f), 0.001f)
    }
}
