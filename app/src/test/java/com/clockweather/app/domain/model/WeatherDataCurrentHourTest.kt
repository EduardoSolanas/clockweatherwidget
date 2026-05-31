package com.clockweather.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class WeatherDataCurrentHourTest {

    @Test
    fun `currentHourForecast returns forecast matching exact current date and hour`() {
        val reference = LocalDateTime.of(2026, 4, 3, 10, 42)
        val weatherData = sampleWeatherData(
            hourlyForecasts = listOf(
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 9, 0), temperature = 14.0),
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 10, 0), temperature = 16.0),
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 11, 0), temperature = 19.0),
            )
        )

        assertEquals(16.0, weatherData.currentHourForecast(reference)?.temperature)
    }

    @Test
    fun `currentHourForecast ignores same hour from another day`() {
        val reference = LocalDateTime.of(2026, 4, 3, 10, 42)
        val weatherData = sampleWeatherData(
            hourlyForecasts = listOf(
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 2, 10, 0), temperature = 12.0),
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 4, 10, 0), temperature = 20.0),
            )
        )

        assertNull(weatherData.currentHourForecast(reference))
    }

    @Test
    fun `currentHourTemperature falls back to current weather when current hour is missing`() {
        val reference = LocalDateTime.of(2026, 4, 3, 10, 42)
        val weatherData = sampleWeatherData(
            currentTemperature = 17.0,
            hourlyForecasts = listOf(
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 11, 0), temperature = 19.0),
            )
        )

        assertEquals(17.0, weatherData.currentHourTemperature(reference), 0.0)
    }

    @Test
    fun `currentHourWeather overlays hourly current readings onto current weather`() {
        val reference = LocalDateTime.of(2026, 4, 3, 10, 42)
        val weatherData = sampleWeatherData(
            hourlyForecasts = listOf(
                sampleHourlyForecast(
                    dateTime = LocalDateTime.of(2026, 4, 3, 10, 0),
                    temperature = 16.0,
                    feelsLike = 15.0,
                    humidity = 72,
                    dewPoint = 11.0,
                    precipitationProbability = 35,
                    weatherCondition = WeatherCondition.RAIN_MODERATE,
                    isDay = false,
                    pressure = 1004.0,
                    windSpeed = 21.0,
                    windDirection = WindDirection.SW,
                    windDirectionDegrees = 225,
                    visibility = 8_000.0,
                    uvIndex = 1.0,
                ),
            )
        )

        val current = weatherData.currentHourWeather(reference)

        assertEquals(16.0, current.temperature, 0.0)
        assertEquals(15.0, current.feelsLikeTemperature, 0.0)
        assertEquals(72, current.humidity)
        assertEquals(35, current.precipitationProbability)
        assertEquals(WeatherCondition.RAIN_MODERATE, current.weatherCondition)
        assertEquals(false, current.isDay)
        assertEquals(21.0, current.windSpeed, 0.0)
        assertEquals(WindDirection.SW, current.windDirection)
        assertEquals(225, current.windDirectionDegrees)
        assertEquals(1.0, current.uvIndex, 0.0)
        assertEquals(30, current.cloudCover)
        assertEquals(12.0, current.windGusts, 0.0)
    }

    private fun sampleWeatherData(
        currentTemperature: Double = 15.0,
        hourlyForecasts: List<HourlyForecast> = emptyList(),
    ) = WeatherData(
        location = Location(
            id = 1L,
            name = "London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
        ),
        currentWeather = CurrentWeather(
            temperature = currentTemperature,
            feelsLikeTemperature = currentTemperature,
            humidity = 60,
            dewPoint = 9.0,
            precipitation = 0.0,
            precipitationProbability = 0,
            weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
            isDay = true,
            pressure = 1012.0,
            windSpeed = 10.0,
            windDirection = WindDirection.N,
            windDirectionDegrees = 0,
            windGusts = 12.0,
            visibility = 10_000.0,
            uvIndex = 5.0,
            cloudCover = 30,
            lastUpdated = LocalDateTime.of(2026, 4, 3, 10, 15),
        ),
        hourlyForecasts = hourlyForecasts,
        dailyForecasts = emptyList(),
    )

    private fun sampleHourlyForecast(
        dateTime: LocalDateTime,
        temperature: Double,
        feelsLike: Double = temperature,
        humidity: Int = 60,
        dewPoint: Double = 9.0,
        precipitationProbability: Int = 0,
        weatherCondition: WeatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
        isDay: Boolean = true,
        pressure: Double = 1012.0,
        windSpeed: Double = 10.0,
        windDirection: WindDirection = WindDirection.N,
        windDirectionDegrees: Int = 0,
        visibility: Double = 10_000.0,
        uvIndex: Double = 5.0,
    ) = HourlyForecast(
        dateTime = dateTime,
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        dewPoint = dewPoint,
        precipitationProbability = precipitationProbability,
        weatherCondition = weatherCondition,
        isDay = isDay,
        pressure = pressure,
        windSpeed = windSpeed,
        windDirection = windDirection,
        windDirectionDegrees = windDirectionDegrees,
        visibility = visibility,
        uvIndex = uvIndex,
    )
}
