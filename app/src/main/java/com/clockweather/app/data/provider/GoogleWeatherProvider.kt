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
            val hourlyPageSize = (days * 24).coerceIn(24, 240)
            val lat = location.latitude
            val lon = location.longitude

            val currentDeferred = async {
                googleWeatherApi.getCurrentConditions(apiKey, lat, lon)
            }
            val hourlyDeferred = async {
                googleWeatherApi.getHourlyForecast(apiKey, lat, lon, pageSize = hourlyPageSize)
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

    /**
     * Widget-optimised fetch: 2 parallel requests instead of 3.
     * Skips hourly forecast (widgets don't display hourly data) and
     * requests exactly 8 days to fill the forecast strip.
     */
    override suspend fun fetchWidgetWeatherData(location: Location): WeatherData =
        coroutineScope {
            val lat = location.latitude
            val lon = location.longitude

            val currentDeferred = async {
                googleWeatherApi.getCurrentConditions(apiKey, lat, lon)
            }
            val dailyDeferred = async {
                googleWeatherApi.getDailyForecast(apiKey, lat, lon, pageSize = 8)
            }

            mapper.mapToWeatherData(
                current  = currentDeferred.await(),
                hourly   = null,
                daily    = dailyDeferred.await(),
                location = location
            )
        }
}
