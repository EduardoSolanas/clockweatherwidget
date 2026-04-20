package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.OpenWeatherMapMapper
import com.clockweather.app.data.remote.api.OpenWeatherMapApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import javax.inject.Inject
import javax.inject.Named

class OpenWeatherMapProvider @Inject constructor(
    private val api: OpenWeatherMapApi,
    @Named("openWeatherMapApiKey") private val apiKey: String,
    private val mapper: OpenWeatherMapMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData {
        val response = api.getOneCall(
            latitude = location.latitude,
            longitude = location.longitude,
            apiKey = apiKey,
            units = "metric",
            exclude = "minutely,alerts"
        )
        return mapper.mapToWeatherData(response, location).let { weatherData ->
            weatherData.copy(dailyForecasts = weatherData.dailyForecasts.take(forecastDays.coerceIn(1, 8)))
        }
    }

    override suspend fun fetchWidgetWeatherData(location: Location): WeatherData {
        val response = api.getOneCall(
            latitude = location.latitude,
            longitude = location.longitude,
            apiKey = apiKey,
            units = "metric",
            exclude = "minutely,hourly,alerts"
        )
        return mapper.mapToWeatherData(response, location)
    }
}
