package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.isAirQualityFresh
import com.clockweather.app.domain.model.isPollenFresh
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.util.TimeZone
import javax.inject.Inject

class OpenMeteoWeatherProvider @Inject constructor(
    private val openMeteoWeatherApi: OpenMeteoWeatherApi,
    private val openMeteoAirQualityApi: OpenMeteoAirQualityApi,
    private val mapper: WeatherDtoMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(
        location: Location,
        forecastDays: Int,
        cachedData: WeatherData?
    ): WeatherData = coroutineScope {
        val days = forecastDays.coerceIn(1, 16)
        val timezone = TimeZone.getDefault().id
        val referenceDateTime = LocalDateTime.now()
        val cachedLastUpdated = cachedData?.currentWeather?.lastUpdated

        val pollenIsFresh = isPollenFresh(
            dailyForecasts = cachedData?.dailyForecasts.orEmpty(),
            lastUpdated = cachedLastUpdated,
            referenceDateTime = referenceDateTime,
            requiredDays = days
        )
        val airQualityIsFresh = isAirQualityFresh(
            airQuality = cachedData?.airQuality,
            lastUpdated = cachedLastUpdated,
            referenceDateTime = referenceDateTime
        )

        val weatherDeferred = async {
            openMeteoWeatherApi.getWeatherForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                timezone = timezone,
                forecastDays = days
            )
        }

        // Open-Meteo Air Quality API provides both AQI and Pollen in the same response.
        // If both are fresh in cache, we skip the air quality network call completely.
        val needAirQuality = !pollenIsFresh || !airQualityIsFresh

        val airQualityDeferred = async {
            if (!needAirQuality) {
                return@async null
            }
            runCatching {
                openMeteoAirQualityApi.getAirQuality(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timezone = timezone,
                    forecastDays = days
                )
            }.getOrNull()
        }

        val cachedPollenByDate = cachedData?.dailyForecasts?.associate { it.date to it.pollen }.orEmpty()

        mapper.mapToWeatherData(
            response = weatherDeferred.await(),
            location = location,
            airQualityResponse = airQualityDeferred.await(),
            cachedAirQuality = cachedData?.airQuality,
            cachedPollenByDate = cachedPollenByDate
        )
    }
}
