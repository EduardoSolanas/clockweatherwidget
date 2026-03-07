package com.clockweather.app.domain.model

import java.time.LocalDateTime

data class HourlyForecast(
    val dateTime: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val dewPoint: Double,
    val precipitationProbability: Int,
    val weatherCondition: WeatherCondition,
    val isDay: Boolean,
    val pressure: Double,
    val windSpeed: Double,
    val windDirection: WindDirection,
    val windDirectionDegrees: Int,
    val visibility: Double,
    val uvIndex: Double
)

