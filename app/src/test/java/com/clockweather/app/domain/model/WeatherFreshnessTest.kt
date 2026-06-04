package com.clockweather.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WeatherFreshnessTest {

    private val referenceDateTime = LocalDateTime.of(2026, 4, 3, 10, 42)

    @Test
    fun `weather is not fresh when null`() {
        assertFalse(
            isWeatherDataFresh(
                weather = null,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
            )
        )
    }

    @Test
    fun `weather is fresh when current hourly and daily coverage are sufficient`() {
        val weather = sampleWeatherData()

        assertTrue(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
            )
        )
    }

    @Test
    fun `weather is not fresh when current weather is older than max age`() {
        val weather = sampleWeatherData(
            lastUpdated = referenceDateTime.minusMinutes(31),
        )

        assertFalse(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
                maxAgeMinutes = 30,
            )
        )
    }

    @Test
    fun `weather is not fresh when current hour forecast is missing`() {
        val weather = sampleWeatherData(
            hourlyForecasts = hourlyForecastsFrom(referenceDateTime.plusHours(1), count = 24),
        )

        assertFalse(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
            )
        )
    }

    @Test
    fun `weather is not fresh when next 24 hourly forecasts are incomplete`() {
        val weather = sampleWeatherData(
            hourlyForecasts = hourlyForecastsFrom(referenceDateTime, count = 23),
        )

        assertFalse(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
            )
        )
    }

    @Test
    fun `weather is not fresh when daily forecasts do not cover requested days`() {
        val weather = sampleWeatherData(
            dailyForecasts = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 2),
        )

        assertFalse(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
            )
        )
    }

    private fun sampleWeatherData(
        lastUpdated: LocalDateTime = referenceDateTime.minusMinutes(10),
        hourlyForecasts: List<HourlyForecast> = hourlyForecastsFrom(referenceDateTime, count = 24),
        dailyForecasts: List<DailyForecast> = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 3),
    ) = WeatherData(
        location = Location(
            id = 1L,
            name = "London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
            timezone = "auto",
        ),
        currentWeather = CurrentWeather(
            temperature = 15.0,
            feelsLikeTemperature = 15.0,
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
            lastUpdated = lastUpdated,
        ),
        hourlyForecasts = hourlyForecasts,
        dailyForecasts = dailyForecasts,
    )

    private fun hourlyForecastsFrom(
        start: LocalDateTime,
        count: Int,
    ): List<HourlyForecast> {
        val firstHour = start.withMinute(0).withSecond(0).withNano(0)
        return (0 until count).map { offset ->
            HourlyForecast(
                dateTime = firstHour.plusHours(offset.toLong()),
                temperature = 15.0 + offset,
                feelsLike = 15.0 + offset,
                humidity = 60,
                dewPoint = 9.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 5.0,
            )
        }
    }

    private fun dailyForecastsFrom(
        start: LocalDate,
        count: Int,
    ): List<DailyForecast> = (0 until count).map { offset ->
        DailyForecast(
            date = start.plusDays(offset.toLong()),
            weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
            temperatureMax = 20.0,
            temperatureMin = 11.0,
            feelsLikeMax = 20.0,
            feelsLikeMin = 11.0,
            sunrise = LocalTime.of(6, 0),
            sunset = LocalTime.of(19, 0),
            daylightDurationSeconds = 36_000.0,
            precipitationSum = 0.0,
            precipitationProbability = 0,
            windSpeedMax = 10.0,
            windDirectionDominant = WindDirection.N,
            windDirectionDegrees = 0,
            uvIndexMax = 5.0,
            averageHumidity = 60,
            averagePressure = 1012.0,
        )
    }
}
