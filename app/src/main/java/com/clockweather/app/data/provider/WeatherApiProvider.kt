package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherApiMapper
import com.clockweather.app.data.remote.api.WeatherApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import javax.inject.Inject
import javax.inject.Named

/**
 * WeatherAPI.com implementation of [WeatherDataProvider].
 * Free plan caps at 3 forecast days; paid plans support up to 14.
 */
class WeatherApiProvider @Inject constructor(
    private val weatherApi: WeatherApi,
    @Named("weatherApiKey") private val apiKey: String,
    private val mapper: WeatherApiMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData {
        val query = "${location.latitude},${location.longitude}"
        val response = weatherApi.getForecast(
            apiKey = apiKey,
            query = query,
            days = forecastDays
        )
        return mapper.mapToWeatherData(response, location)
    }

    /**
     * Widget-optimised fetch: requests only 3 days (free-plan maximum) and
     * lets the mapper drop any hourly data that isn't needed for widget display.
     */
    override suspend fun fetchWidgetWeatherData(location: Location): WeatherData {
        val query = "${location.latitude},${location.longitude}"
        val response = weatherApi.getForecast(
            apiKey = apiKey,
            query = query,
            days = 3
        )
        return mapper.mapToWeatherData(response, location)
    }
}
