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

fun WeatherData.currentHourTemperature(
    referenceDateTime: LocalDateTime = LocalDateTime.now()
): Double = currentHourForecast(referenceDateTime)?.temperature ?: currentWeather.temperature

fun WeatherData.currentHourWeather(
    referenceDateTime: LocalDateTime = LocalDateTime.now()
): CurrentWeather {
    val hour = currentHourForecast(referenceDateTime) ?: return currentWeather
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
