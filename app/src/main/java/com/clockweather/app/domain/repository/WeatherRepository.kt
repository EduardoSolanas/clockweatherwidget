package com.clockweather.app.domain.repository

import com.clockweather.app.data.provider.HourlyScope
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun getWeatherData(location: Location): Flow<WeatherData?>
    suspend fun ensureFreshWeatherData(
        location: Location,
        forecastDays: Int = 7,
        maxAgeMinutes: Long? = null,
        hourlyScope: HourlyScope = HourlyScope.NEAR_TERM,
    )
    suspend fun forceRefreshWeatherData(
        location: Location,
        forecastDays: Int = 7,
        hourlyScope: HourlyScope = HourlyScope.NEAR_TERM,
    )
}
