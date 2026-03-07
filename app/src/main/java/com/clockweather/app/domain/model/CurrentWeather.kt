package com.clockweather.app.domain.model

import java.time.LocalDateTime

data class CurrentWeather(
    val temperature: Double,
    val feelsLikeTemperature: Double,
    val humidity: Int,
    val dewPoint: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val weatherCondition: WeatherCondition,
    val isDay: Boolean,
    val pressure: Double,
    val windSpeed: Double,
    val windDirection: WindDirection,
    val windDirectionDegrees: Int,
    val windGusts: Double,
    val visibility: Double,
    val uvIndex: Double,
    val cloudCover: Int,
    val lastUpdated: LocalDateTime
)
