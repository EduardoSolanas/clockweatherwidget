package com.clockweather.app.domain.model

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal fun isWeatherDataFresh(
    weather: WeatherData?,
    referenceDateTime: LocalDateTime,
    requiredForecastDays: Int,
    maxAgeMinutes: Long = 30,
): Boolean {
    if (weather == null) return false

    val referenceHour = referenceDateTime.truncatedTo(ChronoUnit.HOURS)
    val today = referenceDateTime.toLocalDate()

    if (weather.currentWeather.lastUpdated.isBefore(referenceDateTime.minusMinutes(maxAgeMinutes))) {
        return false
    }
    if (weather.currentWeather.lastUpdated.toLocalDate().isBefore(today)) {
        return false
    }

    val futureHours = weather.hourlyForecasts
        .asSequence()
        .filter { !it.dateTime.truncatedTo(ChronoUnit.HOURS).isBefore(referenceHour) }
        .sortedBy { it.dateTime }
        .toList()

    if (futureHours.firstOrNull()?.dateTime?.truncatedTo(ChronoUnit.HOURS) != referenceHour) {
        return false
    }
    if (futureHours.size < 24) return false

    val coveredDays = weather.dailyForecasts
        .filter { !it.date.isBefore(today) }
        .distinctBy { it.date }
        .count()

    return coveredDays >= requiredForecastDays.coerceAtLeast(1)
}
