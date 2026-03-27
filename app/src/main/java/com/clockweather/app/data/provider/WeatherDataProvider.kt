package com.clockweather.app.data.provider

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData

/**
 * Abstraction over a weather data source.
 *
 * Each implementation fetches and maps raw API data into domain [WeatherData].
 * The repository uses this interface so that swapping providers (WeatherAPI.com,
 * Google Weather, Open-Meteo, etc.) requires no changes above the data layer.
 */
interface WeatherDataProvider {
    suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData

    /**
     * Fetches a lightweight snapshot suitable for widget display.
     * Providers may omit hourly data or reduce forecast days to minimise
     * network usage — widgets only need current conditions + daily forecasts.
     */
    suspend fun fetchWidgetWeatherData(location: Location): WeatherData
}
