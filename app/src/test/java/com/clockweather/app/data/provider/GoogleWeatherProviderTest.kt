package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GoogleWeatherProviderTest {

    private val googleWeatherApi: GoogleWeatherApi = mockk()
    private val mapper: GoogleWeatherMapper = mockk()
    private val fakeWeatherData: WeatherData = mockk()

    private val provider = GoogleWeatherProvider(
        googleWeatherApi = googleWeatherApi,
        apiKey = "test-key",
        mapper = mapper
    )

    private val location = Location(
        id = 1L,
        name = "London",
        country = "GB",
        latitude = 51.5074,
        longitude = -0.1278
    )

    private val currentDto: GoogleCurrentConditionsDto = mockk()
    private val hourlyDto: GoogleHourlyForecastResponseDto = mockk()
    private val dailyDto: GoogleDailyForecastResponseDto = mockk()

    @Test
    fun `fetchWeatherData requests hourly horizon for seven forecast days`() = runTest {
        stubApiAndMapper()

        provider.fetchWeatherData(location, forecastDays = 7)

        coVerify(exactly = 1) {
            googleWeatherApi.getDailyForecast("test-key", location.latitude, location.longitude, pageSize = 7)
        }
        coVerify(exactly = 1) {
            googleWeatherApi.getHourlyForecast("test-key", location.latitude, location.longitude, pageSize = 168)
        }
    }

    @Test
    fun `fetchWeatherData caps hourly horizon at provider maximum`() = runTest {
        stubApiAndMapper()

        provider.fetchWeatherData(location, forecastDays = 14)

        coVerify(exactly = 1) {
            googleWeatherApi.getDailyForecast("test-key", location.latitude, location.longitude, pageSize = 10)
        }
        coVerify(exactly = 1) {
            googleWeatherApi.getHourlyForecast("test-key", location.latitude, location.longitude, pageSize = 240)
        }
    }

    @Test
    fun `fetchWidgetWeatherData does not request hourly forecast`() = runTest {
        coEvery {
            googleWeatherApi.getCurrentConditions(any(), any(), any(), any(), any())
        } returns currentDto
        coEvery {
            googleWeatherApi.getDailyForecast(any(), any(), any(), any(), any(), any())
        } returns dailyDto
        every {
            mapper.mapToWeatherData(currentDto, null, dailyDto, location)
        } returns fakeWeatherData

        provider.fetchWidgetWeatherData(location)

        coVerify(exactly = 0) {
            googleWeatherApi.getHourlyForecast(any(), any(), any(), any(), any(), any())
        }
    }

    private fun stubApiAndMapper() {
        coEvery {
            googleWeatherApi.getCurrentConditions(any(), any(), any(), any(), any())
        } returns currentDto
        coEvery {
            googleWeatherApi.getHourlyForecast(any(), any(), any(), any(), any(), any())
        } returns hourlyDto
        coEvery {
            googleWeatherApi.getDailyForecast(any(), any(), any(), any(), any(), any())
        } returns dailyDto
        every {
            mapper.mapToWeatherData(currentDto, hourlyDto, dailyDto, location)
        } returns fakeWeatherData
    }
}

