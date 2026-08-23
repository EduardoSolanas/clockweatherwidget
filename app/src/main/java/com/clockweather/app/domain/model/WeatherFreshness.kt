package com.clockweather.app.domain.model

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Fallback TTL; callers should pass the provider-specific [WeatherProviderType.currentMaxAgeMinutes]. */
internal const val CURRENT_MAX_AGE_MINUTES = 10L

/** Air quality monitoring stations publish data on hourly cadences. */
internal const val AIR_QUALITY_MAX_AGE_MINUTES = 60L

/** Pollen and allergy biological dispersion models publish runs only 1-2 times daily (12-24h). */
internal const val POLLEN_MAX_AGE_MINUTES = 360L // 6 hours

internal fun isAirQualityFresh(
    airQuality: AirQuality?,
    lastUpdated: LocalDateTime?,
    referenceDateTime: LocalDateTime,
    maxAgeMinutes: Long = AIR_QUALITY_MAX_AGE_MINUTES
): Boolean {
    if (airQuality == null || lastUpdated == null) return false
    return lastUpdated.isAfter(referenceDateTime.minusMinutes(maxAgeMinutes))
}

internal fun isPollenFresh(
    dailyForecasts: List<DailyForecast>,
    lastUpdated: LocalDateTime?,
    referenceDateTime: LocalDateTime,
    requiredDays: Int,
    maxAgeMinutes: Long = POLLEN_MAX_AGE_MINUTES
): Boolean {
    if (lastUpdated == null || dailyForecasts.isEmpty()) return false
    if (!lastUpdated.isAfter(referenceDateTime.minusMinutes(maxAgeMinutes))) return false
    val today = referenceDateTime.toLocalDate()
    val coveredWithPollen = dailyForecasts
        .filter { !it.date.isBefore(today) && it.pollen?.hasData == true }
        .distinctBy { it.date }
        .count()
    return coveredWithPollen >= requiredDays.coerceIn(1, 5)
}

internal fun isWeatherDataFresh(
    weather: WeatherData?,
    referenceDateTime: LocalDateTime,
    requiredForecastDays: Int,
    maxAgeMinutes: Long = CURRENT_MAX_AGE_MINUTES,
): Boolean {
    if (weather == null) return false

    val referenceHour = referenceDateTime.truncatedTo(ChronoUnit.HOURS)
    val today = referenceDateTime.toLocalDate()

    if (!weather.currentWeather.lastUpdated.isAfter(referenceDateTime.minusMinutes(maxAgeMinutes))) {
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
