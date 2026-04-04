package com.clockweather.app.domain.repository

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun getWeatherData(location: Location): Flow<WeatherData>
    suspend fun refreshWeatherData(location: Location, forecastDays: Int = 7)
    fun getCachedWeatherData(locationId: Long): Flow<WeatherData?>
}

