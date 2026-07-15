package com.clockweather.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.Instant

data class WeatherData(
    val location: Location,
    val currentWeather: CurrentWeather,
    val hourlyForecasts: List<HourlyForecast>,
    val dailyForecasts: List<DailyForecast>,
    val airQuality: AirQuality? = null
)

// The phone's clock is the single source of truth for "now" everywhere (detail
// screen, widget, current-hour selection). Forecast timestamps are requested from
// the weather API in this same device timezone, so labels and "now" always agree —
// even when the weather location sits in a different timezone than the phone.
fun WeatherData.locationZoneId(): ZoneId = ZoneId.systemDefault()

fun WeatherData.locationReferenceDateTime(
    currentInstant: Instant = Instant.now()
): LocalDateTime = LocalDateTime.ofInstant(currentInstant, locationZoneId())

/**
 * The single source of truth for "what day is it?" in weather logic: today at the
 * weather location, by the real clock. Every weather-date decision (current-day
 * selection, widget high/low anchor, forecast-day list, refresh staleness) derives
 * from this so the widget and the detail screen never disagree.
 *
 * Intentionally independent of [CurrentWeather.lastUpdated] — how fresh the data is
 * is a separate concern, owned by the refresh logic.
 */
fun WeatherData.weatherToday(
    currentInstant: Instant = Instant.now()
): LocalDate = locationReferenceDateTime(currentInstant).toLocalDate()

fun WeatherData.normalizeDailyConditions(): WeatherData {
    val today = locationReferenceDateTime().toLocalDate()
    return copy(
        dailyForecasts = dailyForecasts.map { forecast ->
            if (forecast.date == today) forecast.copy(weatherCondition = currentWeather.weatherCondition)
            else forecast
        }
    )
}

fun WeatherData.currentHourForecast(
    referenceDateTime: LocalDateTime = locationReferenceDateTime()
): HourlyForecast? {
    val referenceHour = referenceDateTime.truncatedTo(ChronoUnit.HOURS)
    return hourlyForecasts
        .sortedBy { it.dateTime }
        .firstOrNull { forecast ->
            forecast.dateTime.truncatedTo(ChronoUnit.HOURS) == referenceHour
        }
}

fun WeatherData.currentDayForecast(
    referenceDateTime: LocalDateTime = locationReferenceDateTime()
): DailyForecast? {
    val referenceDate = referenceDateTime.toLocalDate()
    return dailyForecasts
        .sortedBy { it.date }
        .firstOrNull { forecast -> forecast.date == referenceDate }
}

fun WeatherData.currentDisplayWeather(
    referenceDateTime: LocalDateTime = locationReferenceDateTime()
): CurrentWeather = currentWeather
