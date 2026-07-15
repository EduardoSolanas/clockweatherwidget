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
    fun `weather is fresh at 9 minutes old`() {
        val weather = sampleWeatherData(lastUpdated = referenceDateTime.minusMinutes(9))

        assertTrue(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceDateTime,
                requiredForecastDays = 3,
            )
        )
    }

    @Test
    fun `weather is not fresh at 11 minutes old`() {
        val weather = sampleWeatherData(lastUpdated = referenceDateTime.minusMinutes(11))

        assertFalse(
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
    fun `weather is not fresh when current weather is exactly max age`() {
        val weather = sampleWeatherData(
            lastUpdated = referenceDateTime.minusMinutes(30),
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

    @Test
    fun `weather is not fresh when hour has rolled over since last update`() {
        // Data was fetched at 09:55, cached hourly starts at 09:00; now it is 10:00 — current hour missing.
        val fetchTime = LocalDateTime.of(2026, 4, 3, 9, 55)
        val now = LocalDateTime.of(2026, 4, 3, 10, 5)
        val weather = sampleWeatherData(
            lastUpdated = fetchTime,
            hourlyForecasts = hourlyForecastsFrom(fetchTime, count = 24),
        )

        assertFalse(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = now,
                requiredForecastDays = 3,
            )
        )
    }

    @Test
    fun `weather is not fresh when day has rolled over in a different timezone`() {
        // Simulate a location in UTC+10 where it is already the next day even though
        // the device (or test reference) is still at the previous date.
        // We do this by setting referenceDateTime to midnight (day boundary) and
        // lastUpdated to the previous day.
        val previousDay = LocalDate.of(2026, 4, 3)
        val today = LocalDate.of(2026, 4, 4)
        val referenceAtMidnight = LocalDateTime.of(today, LocalTime.of(0, 5))

        val weather = sampleWeatherData(
            lastUpdated = LocalDateTime.of(previousDay, LocalTime.of(23, 55)),
            // hourly starts from previous day — current-hour slot for today is missing
            hourlyForecasts = hourlyForecastsFrom(LocalDateTime.of(previousDay, LocalTime.of(0, 0)), count = 24),
            dailyForecasts = dailyForecastsFrom(previousDay, count = 3),
        )

        assertFalse(
            isWeatherDataFresh(
                weather = weather,
                referenceDateTime = referenceAtMidnight,
                requiredForecastDays = 3,
            )
        )
    }

    private fun sampleWeatherData(
        lastUpdated: LocalDateTime = referenceDateTime.minusMinutes(5),
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
