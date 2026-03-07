package com.clockweather.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<String>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "temperature_2m_max") val temperatureMax: List<Double>,
    @Json(name = "temperature_2m_min") val temperatureMin: List<Double>,
    @Json(name = "apparent_temperature_max") val apparentTemperatureMax: List<Double>,
    @Json(name = "apparent_temperature_min") val apparentTemperatureMin: List<Double>,
    @Json(name = "sunrise") val sunrise: List<String>,
    @Json(name = "sunset") val sunset: List<String>,
    @Json(name = "daylight_duration") val daylightDuration: List<Double>,
    @Json(name = "precipitation_sum") val precipitationSum: List<Double>,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>,
    @Json(name = "wind_speed_10m_max") val windSpeedMax: List<Double>,
    @Json(name = "wind_direction_10m_dominant") val windDirectionDominant: List<Int>,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double>
)

