package com.clockweather.app.data.provider

import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.BuildConfig
import com.clockweather.app.domain.model.WeatherProviderType

object WeatherProviderPreferences {
    val KEY_WEATHER_PROVIDER = stringPreferencesKey("weather_provider")

    fun defaultProvider(): WeatherProviderType =
        if (BuildConfig.OPENWEATHERMAP_API_KEY.isNotBlank()) WeatherProviderType.OPENWEATHERMAP
        else WeatherProviderType.OPEN_METEO

    fun resolve(rawValue: String?): WeatherProviderType {
        val requested = WeatherProviderType.fromStorageValue(rawValue) ?: defaultProvider()
        return requested.takeIf(::isConfigured) ?: defaultProvider()
    }

    fun availableProviders(): List<WeatherProviderType> =
        WeatherProviderType.entries.filter(::isConfigured)

    fun isConfigured(provider: WeatherProviderType): Boolean = when (provider) {
        WeatherProviderType.OPEN_METEO -> true
        WeatherProviderType.GOOGLE -> BuildConfig.GOOGLE_WEATHER_API_KEY.isNotBlank()
        WeatherProviderType.WEATHER_API -> BuildConfig.WEATHER_API_KEY.isNotBlank()
        WeatherProviderType.OPENWEATHERMAP -> BuildConfig.OPENWEATHERMAP_API_KEY.isNotBlank()
    }
}
