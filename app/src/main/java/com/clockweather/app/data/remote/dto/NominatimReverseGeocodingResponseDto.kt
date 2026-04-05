package com.clockweather.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NominatimReverseGeocodingResponseDto(
    @Json(name = "display_name") val displayName: String?,
    val address: NominatimAddressDto?
)

@JsonClass(generateAdapter = true)
data class NominatimAddressDto(
    val city: String?,
    @Json(name = "city_district") val cityDistrict: String?,
    val suburb: String?,
    val neighbourhood: String?,
    val county: String?,
    val state: String?,
    val town: String?,
    val village: String?,
    val country: String?,
    @Json(name = "country_code") val countryCode: String?
)

