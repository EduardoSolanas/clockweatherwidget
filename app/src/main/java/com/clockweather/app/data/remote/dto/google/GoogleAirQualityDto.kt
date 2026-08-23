package com.clockweather.app.data.remote.dto.google

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleAirQualityLocationDto(
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class GoogleAirQualityRequestDto(
    val location: GoogleAirQualityLocationDto,
    val extraComputations: List<String> = listOf("POLLUTANT_CONCENTRATION", "LOCAL_AQI"),
    val languageCode: String = "en"
)

@JsonClass(generateAdapter = true)
data class GoogleAirQualityIndexDto(
    val code: String? = null,
    val displayName: String? = null,
    val aqi: Int? = null,
    val aqiDisplay: String? = null,
    val category: String? = null,
    val dominantPollutant: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAirQualityConcentrationDto(
    val value: Double? = null,
    val units: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAirQualityPollutantDto(
    val code: String? = null,
    val displayName: String? = null,
    val concentration: GoogleAirQualityConcentrationDto? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAirQualityResponseDto(
    val dateTime: String? = null,
    val regionCode: String? = null,
    val indexes: List<GoogleAirQualityIndexDto>? = null,
    val pollutants: List<GoogleAirQualityPollutantDto>? = null
)
