package com.clockweather.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    val time: List<String>,
    @Json(name = "temperature_2m") val temperature: List<Double>,
    @Json(name = "relative_humidity_2m") val relativeHumidity: List<Int>,
    @Json(name = "dew_point_2m") val dewPoint: List<Double>,
    @Json(name = "apparent_temperature") val apparentTemperature: List<Double>,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "pressure_msl") val pressureMsl: List<Double>,
    val visibility: List<Double>,
    @Json(name = "wind_speed_10m") val windSpeed: List<Double>,
    @Json(name = "wind_direction_10m") val windDirection: List<Int>,
    @Json(name = "uv_index") val uvIndex: List<Double>,
    @Json(name = "is_day") val isDay: List<Int>
)
