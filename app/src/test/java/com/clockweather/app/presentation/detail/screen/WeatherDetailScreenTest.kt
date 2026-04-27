package com.clockweather.app.presentation.detail.screen

import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WindDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class WeatherDetailScreenTest {

    @Test
    fun `normalizeSelectedDayIndex resets out of range index`() {
        assertEquals(0, normalizeSelectedDayIndex(selectedDayIndex = 5, forecastCount = 3))
    }

    @Test
    fun `normalizeSelectedDayIndex resets negative index`() {
        assertEquals(0, normalizeSelectedDayIndex(selectedDayIndex = -1, forecastCount = 3))
    }

    @Test
    fun `normalizeSelectedDayIndex keeps valid index`() {
        assertEquals(2, normalizeSelectedDayIndex(selectedDayIndex = 2, forecastCount = 4))
    }

    @Test
    fun `buildWeatherTopBarTitle keeps location name for today selection`() {
        val forecasts = listOf(buildForecast(LocalDate.of(2026, 3, 26)))

        val title = buildWeatherTopBarTitle(
            locationName = "London",
            selectedDayIndex = 0,
            forecasts = forecasts,
            locale = Locale.UK
        )

        assertEquals("London", title)
    }

    @Test
    fun `buildWeatherTopBarTitle appends selected date for future day`() {
        val forecasts = listOf(
            buildForecast(LocalDate.of(2026, 3, 26)),
            buildForecast(LocalDate.of(2026, 3, 27))
        )

        val title = buildWeatherTopBarTitle(
            locationName = "London",
            selectedDayIndex = 1,
            forecasts = forecasts,
            locale = Locale.UK
        )

        assertEquals("London  ·  Fri, 27 Mar", title)
    }

    @Test
    fun `selectWeatherDetailForecasts sorts by date before applying forecast day limit`() {
        val today = LocalDate.of(2026, 3, 26)
        val forecasts = listOf(
            buildForecast(LocalDate.of(2026, 3, 28)),
            buildForecast(LocalDate.of(2026, 3, 26)),
            buildForecast(LocalDate.of(2026, 3, 27))
        )

        val selected = selectWeatherDetailForecasts(forecasts, forecastDays = 2, today = today)

        assertEquals(
            listOf(LocalDate.of(2026, 3, 26), LocalDate.of(2026, 3, 27)),
            selected.map { it.date }
        )
    }

    @Test
    fun `selectWeatherDetailForecasts filters out past dates`() {
        val today = LocalDate.of(2026, 3, 27)
        val forecasts = listOf(
            buildForecast(LocalDate.of(2026, 3, 25)), // past
            buildForecast(LocalDate.of(2026, 3, 26)), // past
            buildForecast(LocalDate.of(2026, 3, 27)), // today
            buildForecast(LocalDate.of(2026, 3, 28)), // future
            buildForecast(LocalDate.of(2026, 3, 29))  // future
        )

        val selected = selectWeatherDetailForecasts(forecasts, forecastDays = 7, today = today)

        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 27),
                LocalDate.of(2026, 3, 28),
                LocalDate.of(2026, 3, 29)
            ),
            selected.map { it.date }
        )
    }

    private fun buildForecast(date: LocalDate): DailyForecast = DailyForecast(
        date = date,
        weatherCondition = WeatherCondition.CLEAR_DAY,
        temperatureMax = 18.0,
        temperatureMin = 10.0,
        feelsLikeMax = 17.0,
        feelsLikeMin = 9.0,
        sunrise = LocalTime.of(6, 15),
        sunset = LocalTime.of(18, 45),
        daylightDurationSeconds = 45_000.0,
        precipitationSum = 0.5,
        precipitationProbability = 10,
        windSpeedMax = 18.0,
        windDirectionDominant = WindDirection.N,
        windDirectionDegrees = 0,
        uvIndexMax = 4.0,
        averageHumidity = 62,
        averagePressure = 1015.0
    )
}

