package com.clockweather.app.domain.usecase

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.WeatherRepository
import javax.inject.Inject

class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend operator fun invoke(location: Location, forecastDays: Int = 7) =
        weatherRepository.refreshWeatherData(location, forecastDays)
}

