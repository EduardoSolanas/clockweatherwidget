package com.clockweather.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class DailyForecast(
    val date: LocalDate,
    val weatherCondition: WeatherCondition,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val feelsLikeMax: Double,
    val feelsLikeMin: Double,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val daylightDurationSeconds: Double,
    val precipitationSum: Double,
    val precipitationProbability: Int,
    val windSpeedMax: Double,
    val windDirectionDominant: WindDirection,
    val windDirectionDegrees: Int,
    val uvIndexMax: Double,
    val averageHumidity: Int,
    val averagePressure: Double
)
