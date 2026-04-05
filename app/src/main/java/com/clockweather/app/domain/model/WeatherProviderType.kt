package com.clockweather.app.domain.model

import com.clockweather.app.R

/**
 * Selectable weather backend for all network refreshes.
 */
enum class WeatherProviderType(
    val storageValue: String,
    val labelResId: Int,
    val maxForecastDays: Int,
    val supportedForecastDays: List<Int>
) {
    OPEN_METEO(
        storageValue = "open_meteo",
        labelResId = R.string.weather_provider_open_meteo,
        maxForecastDays = 16,
        supportedForecastDays = listOf(7, 14)
    ),
    GOOGLE(
        storageValue = "google",
        labelResId = R.string.weather_provider_google,
        maxForecastDays = 10,
        supportedForecastDays = listOf(7, 10)
    ),
    WEATHER_API(
        storageValue = "weather_api",
        labelResId = R.string.weather_provider_weather_api,
        maxForecastDays = 3,
        supportedForecastDays = listOf(3)
    );

    companion object {
        fun fromStorageValue(value: String?): WeatherProviderType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.storageValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}

