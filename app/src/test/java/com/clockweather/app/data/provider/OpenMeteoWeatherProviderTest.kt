package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class OpenMeteoWeatherProviderTest {

    private val api: OpenMeteoWeatherApi = mockk()
    private val mapper: WeatherDtoMapper = mockk()
    private val provider = OpenMeteoWeatherProvider(api, mapper)

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `fetchWeatherData requests forecast in device timezone not location timezone`() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        val location = Location(
            id = 1L,
            name = "Dhaka",
            country = "BD",
            latitude = 23.81,
            longitude = 90.41,
            timezone = "Asia/Dhaka",
        )
        val response: WeatherResponseDto = mockk()
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
        } returns response
        every { mapper.mapToWeatherData(response, location) } returns mockk<WeatherData>()

        provider.fetchWeatherData(location, forecastDays = 7)

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
    }
}
