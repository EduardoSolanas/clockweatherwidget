package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityResponseDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.TimeZone

class OpenMeteoWeatherProviderTest {

    private val api: OpenMeteoWeatherApi = mockk()
    private val airQualityApi: OpenMeteoAirQualityApi = mockk()
    private val mapper: WeatherDtoMapper = mockk()
    private val provider = OpenMeteoWeatherProvider(api, airQualityApi, mapper)

    private lateinit var originalTimeZone: TimeZone

    private val location = Location(
        id = 1L,
        name = "Dhaka",
        country = "BD",
        latitude = 23.81,
        longitude = 90.41,
        timezone = "Asia/Dhaka",
    )

    private val weatherResponse: WeatherResponseDto = mockk()
    private val airQualityResponse: OpenMeteoAirQualityResponseDto = mockk()
    private val fakeWeatherData: WeatherData = mockk()

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `fetchWeatherData requests forecast and air quality in device timezone`() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

        coEvery {
            api.getWeatherForecast(
                latitude = any(),
                longitude = any(),
                current = any(),
                hourly = any(),
                daily = any(),
                timezone = any(),
                forecastDays = any(),
                windSpeedUnit = any(),
                temperatureUnit = any(),
            )
        } returns weatherResponse

        coEvery {
            airQualityApi.getAirQuality(
                latitude = any(),
                longitude = any(),
                hourly = any(),
                timezone = any(),
                forecastDays = any()
            )
        } returns airQualityResponse

        every { mapper.mapToWeatherData(weatherResponse, location, airQualityResponse) } returns fakeWeatherData

        val result = provider.fetchWeatherData(location, forecastDays = 7)

        assertEquals(fakeWeatherData, result)

        coVerify(exactly = 1) {
            api.getWeatherForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                current = any(),
                hourly = any(),
                daily = any(),
                timezone = "Europe/London",
                forecastDays = 7,
                windSpeedUnit = any(),
                temperatureUnit = any(),
            )
        }

        coVerify(exactly = 1) {
            airQualityApi.getAirQuality(
                latitude = location.latitude,
                longitude = location.longitude,
                hourly = any(),
                timezone = "Europe/London",
                forecastDays = 7
            )
        }

        coVerify(exactly = 1) {
            mapper.mapToWeatherData(weatherResponse, location, airQualityResponse)
        }
    }

    @Test
    fun `fetchWeatherData succeeds with null airQualityResponse when airQualityApi throws exception`() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

        coEvery {
            api.getWeatherForecast(
                latitude = any(),
                longitude = any(),
                current = any(),
                hourly = any(),
                daily = any(),
                timezone = any(),
                forecastDays = any(),
                windSpeedUnit = any(),
                temperatureUnit = any(),
            )
        } returns weatherResponse

        coEvery {
            airQualityApi.getAirQuality(any(), any(), any(), any(), any())
        } throws IOException("Network error")

        every { mapper.mapToWeatherData(weatherResponse, location, null) } returns fakeWeatherData

        val result = provider.fetchWeatherData(location, forecastDays = 7)

        assertEquals(fakeWeatherData, result)

        coVerify(exactly = 1) {
            mapper.mapToWeatherData(weatherResponse, location, null)
        }
    }
}
