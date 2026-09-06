package com.clockweather.app.domain.usecase

import com.clockweather.app.data.provider.RefreshScope
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import javax.inject.Inject

/**
 * [RefreshScope] defaults to [RefreshScope.FOREGROUND] here because this use case serves the app's
 * foreground, which is the only caller that reads past the near-term hours. Background callers
 * go through the repository directly and take its cheaper default.
 */
class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend fun ensureFresh(
        location: Location,
        forecastDays: Int = 7,
        scope: RefreshScope = RefreshScope.FOREGROUND,
    ) = weatherRepository.ensureFreshWeatherData(
        location,
        forecastDays,
        scope = scope,
    )

    suspend fun forceRefresh(
        location: Location,
        forecastDays: Int = 7,
        scope: RefreshScope = RefreshScope.FOREGROUND,
    ) = weatherRepository.forceRefreshWeatherData(location, forecastDays, scope)

    suspend fun forceRefreshThenSaveLocation(
        location: Location,
        forecastDays: Int,
        locationRepository: LocationRepository,
        scope: RefreshScope = RefreshScope.FOREGROUND,
    ): Long {
        weatherRepository.forceRefreshWeatherData(location, forecastDays, scope)
        return locationRepository.saveLocation(location)
    }
}
