package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.remote.api.GoogleAirQualityApi
import com.clockweather.app.data.remote.api.GooglePollenApi
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GooglePollenForecastResponseDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class GoogleWeatherProviderTest {

    private val googleWeatherApi: GoogleWeatherApi = mockk()
    private val googlePollenApi: GooglePollenApi = mockk()
    private val googleAirQualityApi: GoogleAirQualityApi = mockk()
    private val mapper: GoogleWeatherMapper = mockk()
    private val fakeWeatherData: WeatherData = mockk()

    private val provider = GoogleWeatherProvider(
        googleWeatherApi = googleWeatherApi,
        googlePollenApi = googlePollenApi,
        googleAirQualityApi = googleAirQualityApi,
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
    private val pollenDto: GooglePollenForecastResponseDto = mockk()
    private val airQualityDto: GoogleAirQualityResponseDto = mockk()

    @Test
    fun `fetchWeatherData requests weather and pollen forecast`() = runTest {
        stubApiAndMapper()

        val result = provider.fetchWeatherData(location, forecastDays = 7)

        assertEquals(fakeWeatherData, result)
        coVerify(exactly = 1) {
            googleWeatherApi.getDailyForecast("test-key", location.latitude, location.longitude, pageSize = 7)
        }
        coVerify(exactly = 1) {
            googleWeatherApi.getHourlyForecast("test-key", location.latitude, location.longitude, pageSize = 24, pageToken = null)
        }
        coVerify(exactly = 1) {
            googlePollenApi.getPollenForecast("test-key", location.latitude, location.longitude, days = 5)
        }
        coVerify(exactly = 1) {
            mapper.mapToWeatherData(
                current = currentDto,
                hourly = hourlyDto,
                daily = dailyDto,
                pollen = pollenDto,
                airQuality = airQualityDto,
                location = location
            )
        }
    }

    @Test
    fun `fetchWeatherData caps hourly horizon at provider maximum and pollen days at 5`() = runTest {
        stubApiAndMapper()

        provider.fetchWeatherData(location, forecastDays = 14)

        coVerify(exactly = 1) {
            googleWeatherApi.getDailyForecast("test-key", location.latitude, location.longitude, pageSize = 10)
        }
        coVerify(exactly = 1) {
            googleWeatherApi.getHourlyForecast("test-key", location.latitude, location.longitude, pageSize = 24, pageToken = null)
        }
        coVerify(exactly = 1) {
            googlePollenApi.getPollenForecast("test-key", location.latitude, location.longitude, days = 5)
        }
    }

    @Test
    fun `fetchWeatherData succeeds with null pollen when pollen API throws exception`() = runTest {
        stubApiAndMapper()
        coEvery {
            googlePollenApi.getPollenForecast(any(), any(), any(), any(), any(), any())
        } throws IOException("403 Forbidden - Pollen API not enabled")

        val result = provider.fetchWeatherData(location, forecastDays = 7)

        assertEquals(fakeWeatherData, result)
        coVerify(exactly = 1) {
            mapper.mapToWeatherData(
                current = currentDto,
                hourly = hourlyDto,
                daily = dailyDto,
                pollen = null,
                airQuality = airQualityDto,
                location = location
            )
        }
    }

    private fun stubApiAndMapper() {
        coEvery {
            googleWeatherApi.getCurrentConditions(any(), any(), any(), any(), any())
        } returns currentDto
        coEvery {
            googleWeatherApi.getHourlyForecast(any(), any(), any(), any(), any(), any(), any())
        } returns hourlyDto

        every { hourlyDto.forecastHours } returns emptyList()
        every { hourlyDto.nextPageToken } returns null

        coEvery {
            googleWeatherApi.getDailyForecast(any(), any(), any(), any(), any(), any())
        } returns dailyDto

        every { dailyDto.forecastDays } returns emptyList()

        coEvery {
            googlePollenApi.getPollenForecast(any(), any(), any(), any(), any(), any())
        } returns pollenDto

        coEvery {
            googleAirQualityApi.getCurrentConditions(any(), any())
        } returns airQualityDto

        every {
            mapper.mapToWeatherData(
                current = any(),
                hourly = any(),
                daily = any(),
                pollen = any(),
                airQuality = any(),
                location = any()
            )
        } returns fakeWeatherData
    }
}

