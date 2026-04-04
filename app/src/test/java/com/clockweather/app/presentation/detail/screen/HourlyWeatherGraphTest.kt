package com.clockweather.app.presentation.detail.screen

import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WindDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HourlyWeatherGraphTest {

    @Test
    fun `null selected date returns first 24 sorted hourly forecasts`() {
        val baseDate = LocalDate.of(2026, 3, 26)
        val forecasts = (buildForecasts(baseDate.plusDays(1), 12) + buildForecasts(baseDate, 18)).shuffled()

        val scoped = scopedHourlyForecasts(forecasts, selectedDate = null)

        assertEquals(24, scoped.size)
        assertEquals(scoped.sortedBy { it.dateTime }, scoped)
        assertEquals(baseDate, scoped.first().dateTime.toLocalDate())
    }

    @Test
    fun `selected date returns only matching hourly forecasts`() {
        val baseDate = LocalDate.of(2026, 3, 26)
        val forecasts = buildForecasts(baseDate, 6) + buildForecasts(baseDate.plusDays(1), 8)

        val scoped = scopedHourlyForecasts(forecasts, baseDate.plusDays(1))

        assertEquals(8, scoped.size)
        assertEquals(baseDate.plusDays(1), scoped.first().dateTime.toLocalDate())
        assertEquals(baseDate.plusDays(1), scoped.last().dateTime.toLocalDate())
    }

    @Test
    fun `missing selected date falls back to first 24 hours`() {
        val baseDate = LocalDate.of(2026, 3, 26)
        val forecasts = (buildForecasts(baseDate.plusDays(1), 16) + buildForecasts(baseDate, 16)).shuffled()

        val scoped = scopedHourlyForecasts(forecasts, baseDate.plusDays(3))

        assertEquals(24, scoped.size)
        assertEquals(forecasts.sortedBy { it.dateTime }.take(24), scoped)
    }

    @Test
    fun `selection overlay metrics keep middle column full width and centered`() {
        val metrics = currentHourSelectionOverlayMetrics(
            currentIdx = 3,
            hoursCount = 8,
            columnWidthPx = 58
        )

        assertEquals(CurrentHourSelectionOverlayMetrics(offsetXPx = 174, widthPx = 58), metrics)
    }

    @Test
    fun `selection overlay metrics keep first column full width`() {
        val metrics = currentHourSelectionOverlayMetrics(
            currentIdx = 0,
            hoursCount = 8,
            columnWidthPx = 58
        )

        assertEquals(CurrentHourSelectionOverlayMetrics(offsetXPx = 0, widthPx = 58), metrics)
    }

    @Test
    fun `selection overlay metrics keep last column full width`() {
        val metrics = currentHourSelectionOverlayMetrics(
            currentIdx = 7,
            hoursCount = 8,
            columnWidthPx = 58
        )

        assertEquals(CurrentHourSelectionOverlayMetrics(offsetXPx = 406, widthPx = 58), metrics)
    }

    private fun buildForecasts(date: LocalDate, count: Int): List<HourlyForecast> =
        (0 until count).map { hour ->
            HourlyForecast(
                dateTime = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)),
                temperature = 10.0 + hour,
                feelsLike = 9.0 + hour,
                humidity = 70,
                dewPoint = 4.0,
                precipitationProbability = 20,
                weatherCondition = WeatherCondition.CLEAR_DAY,
                isDay = hour in 6..18,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 3.0
            )
        }
}




