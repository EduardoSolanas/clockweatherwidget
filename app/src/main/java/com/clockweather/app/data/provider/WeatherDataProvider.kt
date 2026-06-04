package com.clockweather.app.data.provider

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData

/**
 * Abstraction over a weather data source.
 *
 * Each implementation fetches and maps raw API data into domain [WeatherData].
 * The repository uses this interface so that swapping providers (Google Weather,
 * Open-Meteo, OpenWeatherMap, etc.) requires no changes above the data layer.
 */
interface WeatherDataProvider {
    suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData
}
