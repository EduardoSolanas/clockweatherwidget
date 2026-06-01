package com.clockweather.app.presentation.settings

import com.clockweather.app.domain.model.WeatherProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastDaysDefaultTest {

    @Test
    fun `returns 14 for tablet width 600dp`() {
        assertEquals(14, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 600))
    }

    @Test
    fun `returns 14 for large tablet 840dp`() {
        assertEquals(14, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 840))
    }

    @Test
    fun `returns 7 for phone width 599dp`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 599))
    }

    @Test
    fun `returns 7 for typical phone width 360dp`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 360))
    }

    @Test
    fun `returns 7 for small phone width 320dp`() {
        assertEquals(7, SettingsViewModel.smartDefaultForecastDays(screenWidthDp = 320))
    }

    @Test
    fun `google normalizes 14 forecast days down to 10`() {
        assertEquals(
            10,
            SettingsViewModel.normalizeForecastDaysForProvider(14, WeatherProviderType.GOOGLE)
        )
    }

    @Test
    fun `open meteo keeps 14 day selection`() {
        assertEquals(
            14,
            SettingsViewModel.normalizeForecastDaysForProvider(14, WeatherProviderType.OPEN_METEO)
        )
    }
}

