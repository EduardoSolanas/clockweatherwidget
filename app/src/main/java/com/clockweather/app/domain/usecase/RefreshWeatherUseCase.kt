package com.clockweather.app.domain.usecase

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.WeatherRepository
import javax.inject.Inject

class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend fun ensureFresh(location: Location, forecastDays: Int = 7) =
        weatherRepository.ensureFreshWeatherData(location, forecastDays)

    suspend fun forceRefresh(location: Location, forecastDays: Int = 7) =
        weatherRepository.forceRefreshWeatherData(location, forecastDays)
}

