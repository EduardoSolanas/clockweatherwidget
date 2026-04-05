package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import javax.inject.Inject

class OpenMeteoWeatherProvider @Inject constructor(
    private val openMeteoWeatherApi: OpenMeteoWeatherApi,
    private val mapper: WeatherDtoMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData {
        val response = openMeteoWeatherApi.getWeatherForecast(
            latitude = location.latitude,
            longitude = location.longitude,
            forecastDays = forecastDays.coerceIn(1, 16)
        )
        return mapper.mapToWeatherData(response, location)
    }

    override suspend fun fetchWidgetWeatherData(location: Location): WeatherData =
        fetchWeatherData(location, forecastDays = 7)
}

