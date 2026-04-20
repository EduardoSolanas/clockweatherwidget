package com.clockweather.app.data.provider

import com.clockweather.app.domain.model.WeatherProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherProviderPreferencesTest {

    private val expectedDefaultProvider = if (
        WeatherProviderPreferences.isConfigured(WeatherProviderType.OPENWEATHERMAP)
    ) {
        WeatherProviderType.OPENWEATHERMAP
    } else {
        WeatherProviderType.OPEN_METEO
    }

    @Test
    fun `fromStorageValue accepts enum name and storage value`() {
        assertEquals(
            WeatherProviderType.OPEN_METEO,
            WeatherProviderType.fromStorageValue("open_meteo")
        )
        assertEquals(
            WeatherProviderType.OPEN_METEO,
            WeatherProviderType.fromStorageValue("OPEN_METEO")
        )
    }

    @Test
    fun `resolve invalid provider falls back to default`() {
        assertEquals(
            WeatherProviderPreferences.defaultProvider(),
            WeatherProviderPreferences.resolve("not_a_provider")
        )
    }

    @Test
    fun `available providers always include open meteo`() {
        assertTrue(WeatherProviderPreferences.availableProviders().contains(WeatherProviderType.OPEN_METEO))
    }

    @Test
    fun `default provider resolves to configured default when available`() {
        assertEquals(
            expectedDefaultProvider,
            WeatherProviderPreferences.defaultProvider()
        )
    }

    @Test
    fun `resolve handles openweathermap storage value based on configuration`() {
        assertEquals(
            expectedDefaultProvider,
            WeatherProviderPreferences.resolve("openweathermap")
        )
    }
}
