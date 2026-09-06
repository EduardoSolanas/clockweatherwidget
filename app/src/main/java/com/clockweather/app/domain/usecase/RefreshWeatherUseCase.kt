package com.clockweather.app.domain.usecase

import com.clockweather.app.data.provider.HourlyScope
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import javax.inject.Inject

/**
 * [HourlyScope] defaults to [HourlyScope.EXTENDED] here because this use case serves the app's
 * foreground, which is the only caller that reads past the near-term hours. Background callers
 * go through the repository directly and take its cheaper default.
 */
class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend fun ensureFresh(
        location: Location,
        forecastDays: Int = 7,
        hourlyScope: HourlyScope = HourlyScope.EXTENDED,
    ) = weatherRepository.ensureFreshWeatherData(
        location,
        forecastDays,
        hourlyScope = hourlyScope,
    )

    suspend fun forceRefresh(
        location: Location,
        forecastDays: Int = 7,
        hourlyScope: HourlyScope = HourlyScope.EXTENDED,
    ) = weatherRepository.forceRefreshWeatherData(location, forecastDays, hourlyScope)

    suspend fun forceRefreshThenSaveLocation(
        location: Location,
        forecastDays: Int,
        locationRepository: LocationRepository,
        hourlyScope: HourlyScope = HourlyScope.EXTENDED,
    ): Long {
        weatherRepository.forceRefreshWeatherData(location, forecastDays, hourlyScope)
        return locationRepository.saveLocation(location)
    }
}
