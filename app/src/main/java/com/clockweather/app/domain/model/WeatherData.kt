package com.clockweather.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class WeatherData(
    val location: Location,
    val currentWeather: CurrentWeather,
    val hourlyForecasts: List<HourlyForecast>,
    val dailyForecasts: List<DailyForecast>,
    val airQuality: AirQuality? = null
)

fun WeatherData.normalizeDailyConditions(): WeatherData {
    val today = LocalDate.now()
    return copy(
        dailyForecasts = dailyForecasts.map { forecast ->
            if (forecast.date == today) forecast.copy(weatherCondition = currentWeather.weatherCondition)
            else forecast
        }
    )
}

fun WeatherData.currentHourForecast(
    referenceDateTime: LocalDateTime = LocalDateTime.now()
): HourlyForecast? {
    val referenceHour = referenceDateTime.truncatedTo(ChronoUnit.HOURS)
    return hourlyForecasts
        .sortedBy { it.dateTime }
        .firstOrNull { forecast ->
            forecast.dateTime.truncatedTo(ChronoUnit.HOURS) == referenceHour
        }
}

fun WeatherData.currentDayForecast(
    referenceDateTime: LocalDateTime = LocalDateTime.now()
): DailyForecast? {
    val referenceDate = referenceDateTime.toLocalDate()
    return dailyForecasts
        .sortedBy { it.date }
        .firstOrNull { forecast -> forecast.date == referenceDate }
}

fun WeatherData.currentDisplayWeather(
    referenceDateTime: LocalDateTime = LocalDateTime.now()
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
