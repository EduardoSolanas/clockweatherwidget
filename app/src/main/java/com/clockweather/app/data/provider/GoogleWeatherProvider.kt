package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.remote.api.GoogleAirQualityApi
import com.clockweather.app.data.remote.api.GooglePollenApi
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.dto.google.GoogleAirQualityLocationDto
import com.clockweather.app.data.remote.dto.google.GoogleAirQualityRequestDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.isAirQualityFresh
import com.clockweather.app.domain.model.isPollenFresh
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Named

/**
 * Google Weather API implementation of [WeatherDataProvider].
 * Makes parallel requests (current conditions, horizon-sized hourly, N-day daily, 5-day pollen forecast, and air quality)
 * and merges them into a single [WeatherData] domain object.
 *
 * Implements tiered TTL caching:
 * - Current weather & hourly: Refreshed on every sync.
 * - Air Quality: Reused if <1 hour old (saves Google AQI API requests).
 * - Pollen & Allergies: Reused if <6 hours old (saves Google Pollen API requests).
 * - When >5 days are requested, Open-Meteo Air Quality is used as a seamless fallback for days 6..10.
 */
class GoogleWeatherProvider @Inject constructor(
    private val googleWeatherApi: GoogleWeatherApi,
    private val googlePollenApi: GooglePollenApi,
    private val googleAirQualityApi: GoogleAirQualityApi,
    private val openMeteoAirQualityApi: OpenMeteoAirQualityApi,
    @Named("googleWeatherApiKey") private val apiKey: String,
    private val mapper: GoogleWeatherMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(
        location: Location,
        forecastDays: Int,
        cachedData: WeatherData?
    ): WeatherData = coroutineScope {
        val days = forecastDays.coerceIn(1, 10)
        val totalTargetHours = days * 24
        val lat = location.latitude
        val lon = location.longitude
        val referenceDateTime = LocalDateTime.now()
        val cachedLastUpdated = cachedData?.currentWeather?.lastUpdated

        val pollenIsFresh = isPollenFresh(
            dailyForecasts = cachedData?.dailyForecasts.orEmpty(),
            lastUpdated = cachedLastUpdated,
            referenceDateTime = referenceDateTime,
            requiredDays = minOf(days, 5)
        )

        val airQualityIsFresh = isAirQualityFresh(
            airQuality = cachedData?.airQuality,
            lastUpdated = cachedLastUpdated,
            referenceDateTime = referenceDateTime
        )

        val currentDeferred = async {
            googleWeatherApi.getCurrentConditions(apiKey, lat, lon)
        }

        val hourlyDeferred = async {
            val allHours = mutableListOf<com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastDto>()
            var pageToken: String? = null

            // Keep fetching until we hit our target or the API runs out of pages
            while (allHours.size < totalTargetHours) {
                val response = googleWeatherApi.getHourlyForecast(
                    apiKey = apiKey,
                    latitude = lat,
                    longitude = lon,
                    hours = totalTargetHours,
                    pageSize = 24,
                    pageToken = pageToken
                )
                allHours.addAll(response.forecastHours)

                pageToken = response.nextPageToken
                if (pageToken.isNullOrBlank()) break
            }

            com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto(
                forecastHours = allHours.take(totalTargetHours)
            )
        }

        val dailyDeferred = async {
            googleWeatherApi.getDailyForecast(apiKey, lat, lon, days = days, pageSize = days)
        }

        val pollenDeferred = async {
            if (pollenIsFresh) {
                return@async null to null
            }

            val googlePollen = runCatching {
                val pollenDays = days.coerceIn(1, 5)
                googlePollenApi.getPollenForecast(apiKey, lat, lon, days = pollenDays)
            }.onFailure {
                android.util.Log.w("GoogleWeatherProvider", "Failed to fetch Google pollen data", it)
            }.getOrNull()

            val openMeteoPollen = if (days > 5 || googlePollen == null) {
                runCatching {
                    openMeteoAirQualityApi.getAirQuality(
                        latitude = lat,
                        longitude = lon,
                        forecastDays = days
                    )
                }.onFailure {
                    android.util.Log.w("GoogleWeatherProvider", "Failed to fetch Open-Meteo pollen fallback", it)
                }.getOrNull()
            } else null

            Pair(googlePollen, openMeteoPollen)
        }

        val airQualityDeferred = async {
            if (airQualityIsFresh) {
                return@async null
            }

            runCatching {
                googleAirQualityApi.getCurrentConditions(
                    apiKey = apiKey,
                    body = GoogleAirQualityRequestDto(
                        location = GoogleAirQualityLocationDto(latitude = lat, longitude = lon)
                    )
                )
            }.onFailure {
                android.util.Log.w("GoogleWeatherProvider", "Failed to fetch air quality data", it)
            }.getOrNull()
        }

        val pollenResult = pollenDeferred.await()
        val cachedPollenByDate = cachedData?.dailyForecasts?.associate { it.date to it.pollen }.orEmpty()

        mapper.mapToWeatherData(
            current = currentDeferred.await(),
            hourly  = hourlyDeferred.await(),
            daily   = dailyDeferred.await(),
            pollen  = pollenResult.first,
            openMeteoPollen = pollenResult.second,
            cachedPollenByDate = cachedPollenByDate,
            airQuality = airQualityDeferred.await(),
            cachedAirQuality = cachedData?.airQuality,
            location = location
        )
    }
}
