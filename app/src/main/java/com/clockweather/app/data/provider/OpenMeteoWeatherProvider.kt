package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.TimeZone
import javax.inject.Inject

class OpenMeteoWeatherProvider @Inject constructor(
    private val openMeteoWeatherApi: OpenMeteoWeatherApi,
    private val openMeteoAirQualityApi: OpenMeteoAirQualityApi,
    private val mapper: WeatherDtoMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData =
        coroutineScope {
            val days = forecastDays.coerceIn(1, 16)
            val timezone = TimeZone.getDefault().id

            val weatherDeferred = async {
                openMeteoWeatherApi.getWeatherForecast(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timezone = timezone,
                    forecastDays = days
                )
            }

            val airQualityDeferred = async {
                runCatching {
                    openMeteoAirQualityApi.getAirQuality(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timezone = timezone,
                        forecastDays = days
                    )
                }.getOrNull()
            }

            mapper.mapToWeatherData(
                response = weatherDeferred.await(),
                location = location,
                airQualityResponse = airQualityDeferred.await()
            )
        }

}

