package com.clockweather.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeocodingResponseDto(
    val results: List<GeoLocationDto>?,
    val generationtime_ms: Double?
)

@JsonClass(generateAdapter = true)
data class GeoLocationDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    @Json(name = "feature_code") val featureCode: String?,
    @Json(name = "country_code") val countryCode: String?,
    val country: String?,
    val timezone: String?,
    val population: Long?,
    val admin1: String?,
    val admin2: String?,
    val admin3: String?,
    val admin4: String?
)

