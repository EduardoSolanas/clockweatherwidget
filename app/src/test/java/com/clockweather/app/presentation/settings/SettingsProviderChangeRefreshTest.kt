package com.clockweather.app.presentation.settings

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsProviderChangeRefreshTest {

    private val locationRepository: LocationRepository = mockk()
    private val weatherRepository: WeatherRepository = mockk()

    private val location = Location(
        id = 1L,
        name = "London",
        country = "GB",
        latitude = 51.5074,
        longitude = -0.1278,
    )

    @Test
    fun `provider change refresh uses first saved location immediately`() = runTest {
        every { locationRepository.getSavedLocations() } returns flowOf(listOf(location))
        coEvery { locationRepository.getCurrentLocation() } returns null
        every { locationRepository.getFallbackLocation() } returns location
        coEvery { weatherRepository.refreshWeatherData(any(), any()) } returns Unit

        refreshWeatherForProviderChange(
            locationRepository = locationRepository,
            weatherRepository = weatherRepository,
            forecastDays = 7
        )

        coVerify(exactly = 1) { weatherRepository.refreshWeatherData(location, 7) }
    }
}
