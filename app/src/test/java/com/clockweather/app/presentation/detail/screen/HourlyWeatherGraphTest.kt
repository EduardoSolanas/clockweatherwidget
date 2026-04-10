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

        val scoped = scopedHourlyForecasts(
            forecasts,
            baseDate.plusDays(1),
            LocalDateTime.of(baseDate, java.time.LocalTime.of(12, 0))
        )

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
    fun `selected date with one remaining hour falls forward to next 24 hours`() {
        val baseDate = LocalDate.of(2026, 3, 26)
        val forecasts = buildForecasts(baseDate, 24) +
            listOf(buildForecast(baseDate.plusDays(1), 23)) +
            buildForecasts(baseDate.plusDays(2), 24)

        val scoped = scopedHourlyForecasts(
            forecasts.shuffled(),
            baseDate.plusDays(1),
            LocalDateTime.of(baseDate, java.time.LocalTime.of(12, 0))
        )

        assertEquals(24, scoped.size)
        assertEquals(baseDate.plusDays(1), scoped.first().dateTime.toLocalDate())
        assertEquals(23, scoped.first().dateTime.hour)
        assertEquals(baseDate.plusDays(2), scoped.last().dateTime.toLocalDate())
        assertEquals(22, scoped.last().dateTime.hour)
    }

    @Test
    fun `today selected date returns rolling 24 hours across midnight`() {
        val today = LocalDate.of(2026, 3, 26)
        val forecasts = buildForecasts(today, 24) + buildForecasts(today.plusDays(1), 24)

        val scoped = scopedHourlyForecasts(
            forecasts.shuffled(),
            selectedDate = today,
            referenceDateTime = LocalDateTime.of(today, java.time.LocalTime.of(21, 15))
        )

        assertEquals(24, scoped.size)
        assertEquals(today, scoped.first().dateTime.toLocalDate())
        assertEquals(21, scoped.first().dateTime.hour)
        assertEquals(today.plusDays(1), scoped.last().dateTime.toLocalDate())
        assertEquals(20, scoped.last().dateTime.hour)
    }

    @Test
    fun `today selected date keeps full 24 hour view when one hour remains before midnight`() {
        val today = LocalDate.of(2026, 3, 26)
        val forecasts = buildForecasts(today, 24) + buildForecasts(today.plusDays(1), 24)

        val scoped = scopedHourlyForecasts(
            forecasts.shuffled(),
            selectedDate = today,
            referenceDateTime = LocalDateTime.of(today, java.time.LocalTime.of(23, 5))
        )

        assertEquals(24, scoped.size)
        assertEquals(today, scoped.first().dateTime.toLocalDate())
        assertEquals(23, scoped.first().dateTime.hour)
        assertEquals(today.plusDays(1), scoped.last().dateTime.toLocalDate())
        assertEquals(22, scoped.last().dateTime.hour)
    }

    @Test
    fun `selection overlay metrics keep middle column full width and centered`() {
        val metrics = currentHourSelectionOverlayMetrics(
            currentIdx = 3,
            hoursCount = 8,
            columnWidthPx = 58,
            horizontalInsetPx = 2
        )

        assertEquals(CurrentHourSelectionOverlayMetrics(offsetXPx = 174, widthPx = 58), metrics)
    }

    @Test
    fun `selection overlay metrics keep first column full width`() {
        val metrics = currentHourSelectionOverlayMetrics(
            currentIdx = 0,
            hoursCount = 8,
            columnWidthPx = 58,
            horizontalInsetPx = 2
        )

        assertEquals(CurrentHourSelectionOverlayMetrics(offsetXPx = 2, widthPx = 56), metrics)
    }

    @Test
    fun `selection overlay metrics keep last column full width`() {
        val metrics = currentHourSelectionOverlayMetrics(
            currentIdx = 7,
            hoursCount = 8,
            columnWidthPx = 58,
            horizontalInsetPx = 2
        )

        assertEquals(CurrentHourSelectionOverlayMetrics(offsetXPx = 406, widthPx = 56), metrics)
    }

    // ── resolveCurrentHourIndex ───────────────────────────────────────────────

    @Test
    fun `resolveCurrentHourIndex returns index of first matching hour`() {
        val today = LocalDate.of(2026, 4, 10)
        val hours = buildForecasts(today, 6, startHour = 10)  // hours 10..15

        assertEquals(2, resolveCurrentHourIndex(hours, nowHour = 12))
    }

    @Test
    fun `resolveCurrentHourIndex returns 0 when no hour matches`() {
        val today = LocalDate.of(2026, 4, 10)
        val hours = buildForecasts(today, 4, startHour = 10)  // hours 10..13

        assertEquals(0, resolveCurrentHourIndex(hours, nowHour = 5))
    }

    @Test
    fun `resolveCurrentHourIndex returns 0 for first hour of the day`() {
        val today = LocalDate.of(2026, 4, 10)
        val hours = buildForecasts(today, 8, startHour = 0)  // hours 0..7

        assertEquals(0, resolveCurrentHourIndex(hours, nowHour = 0))
    }

    @Test
    fun `resolveCurrentHourIndex handles midnight rollover (hour 0 after 23)`() {
        val today = LocalDate.of(2026, 4, 10)
        val tomorrow = today.plusDays(1)
        val hours = buildForecasts(today, 4, startHour = 21) +  // 21,22,23
            buildForecasts(tomorrow, 4, startHour = 0)          // 0,1,2,3

        // nowHour=0 belongs to tomorrow's first slot (index 3)
        assertEquals(3, resolveCurrentHourIndex(hours, nowHour = 0))
    }

    private fun buildForecasts(date: LocalDate, count: Int, startHour: Int = 0): List<HourlyForecast> =
        (0 until count).map { i ->
            buildForecast(date, (startHour + i) % 24)
        }

    private fun buildForecast(date: LocalDate, hour: Int): HourlyForecast =
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




