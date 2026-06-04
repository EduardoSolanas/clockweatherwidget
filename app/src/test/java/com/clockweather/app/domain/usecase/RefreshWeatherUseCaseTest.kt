package com.clockweather.app.domain.usecase

import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.WeatherRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
    fun `forceRefresh passes forecastDays 14 to repository`() = runTest {
        coJustRun { repository.forceRefreshWeatherData(location, 14) }

        useCase.forceRefresh(location, forecastDays = 14)

        coVerify(exactly = 1) { repository.forceRefreshWeatherData(location, 14) }
    }

    @Test
    fun `ensureFresh defaults to 7 forecast days when not specified`() = runTest {
        coJustRun { repository.ensureFreshWeatherData(location, 7) }

        useCase.ensureFresh(location)

        coVerify(exactly = 1) { repository.ensureFreshWeatherData(location, 7) }
    }

    @Test
    fun `forceRefresh passes forecastDays 1 to repository`() = runTest {
        coJustRun { repository.forceRefreshWeatherData(location, 1) }

        useCase.forceRefresh(location, forecastDays = 1)

        coVerify(exactly = 1) { repository.forceRefreshWeatherData(location, 1) }
    }
}

