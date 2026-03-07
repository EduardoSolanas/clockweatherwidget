package com.clockweather.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    val time: String,
    @Json(name = "temperature_2m") val temperature: Double,
    @Json(name = "relative_humidity_2m") val relativeHumidity: Int,
    @Json(name = "apparent_temperature") val apparentTemperature: Double,
    val precipitation: Double,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "cloud_cover") val cloudCover: Int,
    @Json(name = "pressure_msl") val pressureMsl: Double,
    @Json(name = "surface_pressure") val surfacePressure: Double,
    @Json(name = "wind_speed_10m") val windSpeed: Double,
    @Json(name = "wind_direction_10m") val windDirection: Int,
    @Json(name = "wind_gusts_10m") val windGusts: Double,
    @Json(name = "is_day") val isDay: Int
)
