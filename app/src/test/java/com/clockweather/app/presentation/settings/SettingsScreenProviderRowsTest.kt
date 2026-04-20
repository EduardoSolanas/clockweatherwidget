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
}
