package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Named

/**
 * Google Weather API implementation of [WeatherDataProvider].
 * Makes 3 parallel requests (current conditions, horizon-sized hourly, N-day daily)
 * and merges them into a single [WeatherData] domain object.
 *
 * Google Weather API supports up to 10 forecast days; requests above that are capped.
 */
class GoogleWeatherProvider @Inject constructor(
    private val googleWeatherApi: GoogleWeatherApi,
    @Named("googleWeatherApiKey") private val apiKey: String,
    private val mapper: GoogleWeatherMapper
) : WeatherDataProvider {

    override suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData =
        coroutineScope {
            val days = forecastDays.coerceIn(1, 10)
            val totalTargetHours = days * 24
            val lat = location.latitude
            val lon = location.longitude

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
                googleWeatherApi.getDailyForecast(apiKey, lat, lon, pageSize = days)
            }

            mapper.mapToWeatherData(
                current = currentDeferred.await(),
                hourly  = hourlyDeferred.await(),
                daily   = dailyDeferred.await(),
                location = location
            )
        }

}
