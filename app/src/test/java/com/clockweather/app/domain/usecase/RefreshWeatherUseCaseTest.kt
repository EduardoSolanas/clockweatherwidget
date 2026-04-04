package com.clockweather.app.domain.usecase

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.WeatherRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * TDD: RefreshWeatherUseCase must forward the forecastDays parameter to the repository.
 *
 * RED → these tests fail until RefreshWeatherUseCase.invoke accepts forecastDays
 *       and WeatherRepository.refreshWeatherData accepts forecastDays.
 */
class RefreshWeatherUseCaseTest {

    private val repository: WeatherRepository = mockk()
    private val useCase = RefreshWeatherUseCase(repository)
    private val location = Location(
        id = 1L,
        name = "London",
        country = "UK",
        latitude = 51.5,
        longitude = -0.1
    )

    @Test
    fun `invoke passes forecastDays 14 to repository`() = runTest {
        coJustRun { repository.refreshWeatherData(location, 14) }

        useCase(location, forecastDays = 14)

        coVerify(exactly = 1) { repository.refreshWeatherData(location, 14) }
    }

    @Test
    fun `invoke defaults to 7 forecast days when not specified`() = runTest {
        coJustRun { repository.refreshWeatherData(location, 7) }

        useCase(location)

        coVerify(exactly = 1) { repository.refreshWeatherData(location, 7) }
    }

    @Test
    fun `invoke passes forecastDays 1 to repository`() = runTest {
        coJustRun { repository.refreshWeatherData(location, 1) }

        useCase(location, forecastDays = 1)

        coVerify(exactly = 1) { repository.refreshWeatherData(location, 1) }
    }
}

