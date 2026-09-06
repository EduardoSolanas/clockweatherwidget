package com.clockweather.app.data.provider

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData

/**
 * What a refresh is worth buying.
 *
 * Google bills per request, and the home-screen widgets render far less than the app does:
 * current conditions, the daily forecast, and the pollen bar when the user has enabled it.
 * They never show the hourly forecast or air quality. Routine background work therefore skips
 * whatever nothing on the home screen can display, and the app fetches the rest when opened.
 *
 * A section that is not fetched is left as it was rather than overwritten with nothing, so
 * turning a section off costs the user no data they already had.
 */
data class RefreshScope(
    val includeHourly: Boolean,
    val includeAirQuality: Boolean,
    val includePollen: Boolean,
) {
    companion object {
        /** Someone is looking at the app, where every section is visible. */
        val FOREGROUND = RefreshScope(
            includeHourly = true,
            includeAirQuality = true,
            includePollen = true,
        )

        /**
         * Keeping the widgets current. Pollen is bought only while the widget's pollen bar is
         * switched on; with it off, nothing on the home screen displays pollen and it becomes
         * another section the app fetches on demand.
         */
        fun background(pollenShownInWidget: Boolean) = RefreshScope(
            includeHourly = false,
            includeAirQuality = false,
            includePollen = pollenShownInWidget,
        )
    }
}

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
        cachedData: WeatherData? = null,
        scope: RefreshScope = RefreshScope.FOREGROUND
    ): WeatherData
}
