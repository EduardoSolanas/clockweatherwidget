package com.clockweather.app.domain.usecase

import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetHourlyForecastUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    operator fun invoke(location: Location): Flow<List<HourlyForecast>> =
        weatherRepository.getWeatherData(location).map { it.hourlyForecasts }
}

