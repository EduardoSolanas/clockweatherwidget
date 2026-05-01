package com.clockweather.app.presentation.settings

import com.clockweather.app.domain.model.WeatherProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenProviderRowsTest {

    @Test
    fun `provider chips are split into multiple rows when providers exceed per-row limit`() {
        val rows = providerChipRows(
            listOf(
                WeatherProviderType.OPENWEATHERMAP,
                WeatherProviderType.OPEN_METEO,
                WeatherProviderType.GOOGLE,
                WeatherProviderType.WEATHER_API
            ),
            maxItemsPerRow = 2
        )

        assertEquals(
            listOf(
                listOf(WeatherProviderType.OPENWEATHERMAP, WeatherProviderType.OPEN_METEO),
                listOf(WeatherProviderType.GOOGLE, WeatherProviderType.WEATHER_API)
            ),
            rows
        )
    }

    @Test
    fun `weather icon style chips are split into rows`() {
        val rows = iconStyleRows(
            listOf(
                SettingsViewModel.ICON_STYLE_GLASS_AI,
                SettingsViewModel.ICON_STYLE_GLASS,
                SettingsViewModel.ICON_STYLE_CLAY,
                SettingsViewModel.ICON_STYLE_NEON
            ),
            maxItemsPerRow = 2
        )

        assertEquals(
            listOf(
                listOf(SettingsViewModel.ICON_STYLE_GLASS_AI, SettingsViewModel.ICON_STYLE_GLASS),
                listOf(SettingsViewModel.ICON_STYLE_CLAY, SettingsViewModel.ICON_STYLE_NEON)
            ),
            rows
        )
    }
}
