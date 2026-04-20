package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.OpenWeatherMapMapper
import com.clockweather.app.data.remote.api.OpenWeatherMapApi
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapOneCallResponseDto
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OpenWeatherMapProviderTest {

    private val api: OpenWeatherMapApi = mockk()
    private val mapper: OpenWeatherMapMapper = mockk()
    private val fakeResponse: OpenWeatherMapOneCallResponseDto = mockk()
    private val fakeWeatherData = WeatherData(
        location = Location(
            id = 99L,
            name = "Stub",
            country = "GB",
            latitude = 0.0,
            longitude = 0.0
        ),
        currentWeather = CurrentWeather(
            temperature = 10.0,
            feelsLikeTemperature = 9.0,
            humidity = 80,
            dewPoint = 6.0,
            precipitation = 0.0,
            precipitationProbability = 0,
            weatherCondition = WeatherCondition.CLEAR_DAY,
            isDay = true,
            pressure = 1012.0,
            windSpeed = 5.0,
            windDirection = WindDirection.N,
            windDirectionDegrees = 0,
            windGusts = 7.0,
            visibility = 10000.0,
            uvIndex = 1.0,
            cloudCover = 0,
            lastUpdated = LocalDateTime.of(2026, 4, 20, 12, 0)
        ),
        hourlyForecasts = listOf(
            HourlyForecast(
                dateTime = LocalDateTime.of(2026, 4, 20, 13, 0),
                temperature = 11.0,
                feelsLike = 10.0,
                humidity = 75,
                dewPoint = 6.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.CLEAR_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 5.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10000.0,
                uvIndex = 2.0
            )
        ),
        dailyForecasts = listOf(
            DailyForecast(
                date = LocalDate.of(2026, 4, 20),
                weatherCondition = WeatherCondition.CLEAR_DAY,
                temperatureMax = 15.0,
                temperatureMin = 8.0,
                feelsLikeMax = 14.0,
                feelsLikeMin = 7.0,
                sunrise = LocalTime.of(6, 0),
                sunset = LocalTime.of(19, 0),
                daylightDurationSeconds = 46800.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 8.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 3.0,
                averageHumidity = 70,
                averagePressure = 1012.0
            )
        )
    )

    private val provider = OpenWeatherMapProvider(
        api = api,
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

    @Test
    fun `fetchWeatherData calls one call endpoint with location and api key`() = runTest {
        coEvery {
            api.getOneCall(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = "test-key",
                units = "metric",
                exclude = "minutely,alerts"
            )
        } returns fakeResponse
        every { mapper.mapToWeatherData(fakeResponse, location) } returns fakeWeatherData

        provider.fetchWeatherData(location, forecastDays = 7)

        coVerify(exactly = 1) {
            api.getOneCall(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = "test-key",
                units = "metric",
                exclude = "minutely,alerts"
            )
        }
        confirmVerified(api)
    }

    @Test
    fun `fetchWidgetWeatherData excludes hourly for lightweight payload`() = runTest {
        coEvery {
            api.getOneCall(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = "test-key",
                units = "metric",
                exclude = "minutely,hourly,alerts"
            )
        } returns fakeResponse
        every { mapper.mapToWeatherData(fakeResponse, location) } returns fakeWeatherData

        provider.fetchWidgetWeatherData(location)

        coVerify(exactly = 1) {
            api.getOneCall(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = "test-key",
                units = "metric",
                exclude = "minutely,hourly,alerts"
            )
        }
        confirmVerified(api)
    }
}
