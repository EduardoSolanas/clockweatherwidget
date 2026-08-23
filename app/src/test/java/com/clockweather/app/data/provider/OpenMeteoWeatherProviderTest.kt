package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityResponseDto
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.PollenData
import com.clockweather.app.domain.model.PollenType
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

        every {
            mapper.mapToWeatherData(
                response = weatherResponse,
                location = location,
                airQualityResponse = airQualityResponse,
                cachedAirQuality = any(),
                cachedPollenByDate = any()
            )
        } returns fakeWeatherData

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
            mapper.mapToWeatherData(
                response = weatherResponse,
                location = location,
                airQualityResponse = airQualityResponse,
                cachedAirQuality = any(),
                cachedPollenByDate = any()
            )
        }
    }

    @Test
    fun `fetchWeatherData skips air quality network call when cached AQI and Pollen are fresh`() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val freshPollen = PollenData(
            grassPollen = PollenType("GRASS", "Grass", true, 2, "Low")
        )
        val cachedDaily = (0..6).map { i ->
            DailyForecast(
                date = today.plusDays(i.toLong()),
                weatherCondition = WeatherCondition.CLEAR_DAY,
                temperatureMax = 20.0,
                temperatureMin = 10.0,
                feelsLikeMax = 20.0,
                feelsLikeMin = 10.0,
                sunrise = LocalTime.of(6, 0),
                sunset = LocalTime.of(18, 0),
                daylightDurationSeconds = 43200.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 10.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 3.0,
                averageHumidity = 50,
                averagePressure = 1013.25,
                pollen = freshPollen
            )
        }

        val cachedWeather = WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 18.0,
                feelsLikeTemperature = 18.0,
                humidity = 50,
                dewPoint = 10.0,
                precipitation = 0.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.CLEAR_DAY,
                isDay = true,
                pressure = 1013.25,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                windGusts = 15.0,
                visibility = 10000.0,
                uvIndex = 3.0,
                cloudCover = 0,
                lastUpdated = now.minusMinutes(20) // 20m ago: AQI (<60m) and Pollen (<360m) are both fresh
            ),
            hourlyForecasts = emptyList(),
            dailyForecasts = cachedDaily,
            airQuality = AirQuality(
                co = 1.0, no2 = 1.0, o3 = 1.0, so2 = 1.0, pm25 = 5.0, pm10 = 10.0, usEpaIndex = 1, gbDefraIndex = 1
            )
        )

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

        every {
            mapper.mapToWeatherData(
                response = weatherResponse,
                location = location,
                airQualityResponse = null,
                cachedAirQuality = cachedWeather.airQuality,
                cachedPollenByDate = any()
            )
        } returns fakeWeatherData

        val result = provider.fetchWeatherData(location, forecastDays = 7, cachedData = cachedWeather)

        assertEquals(fakeWeatherData, result)

        // Verifies that airQuality network call was completely skipped!
        coVerify(exactly = 0) {
            airQualityApi.getAirQuality(any(), any(), any(), any(), any())
        }

        coVerify(exactly = 1) {
            mapper.mapToWeatherData(
                response = weatherResponse,
                location = location,
                airQualityResponse = null,
                cachedAirQuality = cachedWeather.airQuality,
                cachedPollenByDate = any()
            )
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

        every {
            mapper.mapToWeatherData(
                response = weatherResponse,
                location = location,
                airQualityResponse = null,
                cachedAirQuality = any(),
                cachedPollenByDate = any()
            )
        } returns fakeWeatherData

        val result = provider.fetchWeatherData(location, forecastDays = 7)

        assertEquals(fakeWeatherData, result)

        coVerify(exactly = 1) {
            mapper.mapToWeatherData(
                response = weatherResponse,
                location = location,
                airQualityResponse = null,
                cachedAirQuality = any(),
                cachedPollenByDate = any()
            )
        }
    }
}
