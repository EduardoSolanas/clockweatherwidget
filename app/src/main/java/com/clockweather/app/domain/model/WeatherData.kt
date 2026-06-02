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

fun WeatherData.locationZoneId(): ZoneId =
    location.timezone
        .takeUnless { it.isBlank() || it.equals("auto", ignoreCase = true) }
        ?.let { timezone -> runCatching { ZoneId.of(timezone) }.getOrNull() }
        ?: ZoneId.systemDefault()

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
): CurrentWeather {
    val hour = currentHourForecast(referenceDateTime)
    if (hour == null) {
        val day = currentDayForecast(referenceDateTime) ?: return currentWeather
        return currentWeather.copy(
            humidity = day.averageHumidity,
            precipitationProbability = day.precipitationProbability,
            weatherCondition = day.weatherCondition,
            pressure = day.averagePressure,
            windSpeed = day.windSpeedMax,
            windDirection = day.windDirectionDominant,
            windDirectionDegrees = day.windDirectionDegrees,
            uvIndex = day.uvIndexMax,
        )
    }

    return currentWeather.copy(
        temperature = hour.temperature,
        feelsLikeTemperature = hour.feelsLike,
        humidity = hour.humidity,
        dewPoint = hour.dewPoint,
        precipitationProbability = hour.precipitationProbability,
        weatherCondition = hour.weatherCondition,
        isDay = hour.isDay,
        pressure = hour.pressure,
        windSpeed = hour.windSpeed,
        windDirection = hour.windDirection,
        windDirectionDegrees = hour.windDirectionDegrees,
        visibility = hour.visibility,
        uvIndex = hour.uvIndex,
    )
}
