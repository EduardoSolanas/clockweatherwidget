package com.clockweather.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponseDto(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    @Json(name = "generationtime_ms") val generationTimeMs: Double,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int,
    val timezone: String,
    @Json(name = "timezone_abbreviation") val timezoneAbbreviation: String,
    val current: CurrentWeatherDto?,
    @Json(name = "current_units") val currentUnits: Map<String, String>?,
    val hourly: HourlyWeatherDto?,
    @Json(name = "hourly_units") val hourlyUnits: Map<String, String>?,
    val daily: DailyWeatherDto?,
    @Json(name = "daily_units") val dailyUnits: Map<String, String>?

)
