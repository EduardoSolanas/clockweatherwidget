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
/**
 * How much of the hourly forecast a fetch should retrieve.
 *
 * Google bills one request per 24 hours of hourly data, and only the detail screen reads
 * beyond the first day — the widgets use the daily forecast. Routine refreshes therefore ask
 * for [NEAR_TERM] and the app asks for [EXTENDED] when someone opens it.
 */
enum class HourlyScope {
    /** A rolling 24 hours from now: one page, enough for every background caller. */
    NEAR_TERM,

    /** The whole requested forecast, for the detail screen's later days. */
    EXTENDED,
}

interface WeatherDataProvider {
    suspend fun fetchWeatherData(
        location: Location,
        forecastDays: Int,
        cachedData: WeatherData? = null,
        hourlyScope: HourlyScope = HourlyScope.EXTENDED
    ): WeatherData
}
