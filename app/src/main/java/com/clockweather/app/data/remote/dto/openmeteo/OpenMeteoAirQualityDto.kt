package com.clockweather.app.data.remote.dto.openmeteo

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenMeteoAirQualityHourlyDto(
    val time: List<String> = emptyList(),
    @Json(name = "pm10") val pm10: List<Double?>? = null,
    @Json(name = "pm2_5") val pm25: List<Double?>? = null,
    @Json(name = "carbon_monoxide") val carbonMonoxide: List<Double?>? = null,
    @Json(name = "nitrogen_dioxide") val nitrogenDioxide: List<Double?>? = null,
    @Json(name = "sulphur_dioxide") val sulphurDioxide: List<Double?>? = null,
    @Json(name = "ozone") val ozone: List<Double?>? = null,
    @Json(name = "us_aqi") val usAqi: List<Int?>? = null,
    @Json(name = "european_aqi") val europeanAqi: List<Int?>? = null,
    // Pollen variables (grains/m³)
    @Json(name = "alder_pollen") val alderPollen: List<Double?>? = null,
    @Json(name = "birch_pollen") val birchPollen: List<Double?>? = null,
    @Json(name = "grass_pollen") val grassPollen: List<Double?>? = null,
    @Json(name = "mugwort_pollen") val mugwortPollen: List<Double?>? = null,
    @Json(name = "olive_pollen") val olivePollen: List<Double?>? = null,
    @Json(name = "ragweed_pollen") val ragweedPollen: List<Double?>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoAirQualityResponseDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "UTC",
    val hourly: OpenMeteoAirQualityHourlyDto? = null
)
