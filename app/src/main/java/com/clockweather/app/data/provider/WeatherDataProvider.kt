package com.clockweather.app.data.provider

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData

/**
 * Abstraction over a weather data source.
 *
 * Each implementation fetches and maps raw API data into domain [WeatherData].
 * The repository uses this interface so that swapping providers (Google Weather,
 * Open-Meteo, OpenWeatherMap, etc.) requires no changes above the data layer.
 *
 * An optional [cachedData] instance allows providers to implement tiered TTL caching,
 * reusing fresh air quality or pollen data without re-fetching from the network.
 */
interface WeatherDataProvider {
    suspend fun fetchWeatherData(
        location: Location,
        forecastDays: Int,
        cachedData: WeatherData? = null
    ): WeatherData
}
