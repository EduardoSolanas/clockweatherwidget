package com.clockweather.app.data.remote.dto.google

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GooglePollenDateDto(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0
)

@JsonClass(generateAdapter = true)
data class GooglePollenIndexInfoDto(
    val code: String? = null,
    val displayName: String? = null,
    val value: Int? = null,
    val category: String? = null,
    val indexDescription: String? = null
)

@JsonClass(generateAdapter = true)
data class GooglePollenTypeInfoDto(
    val code: String = "",
    val displayName: String = "",
    val inSeason: Boolean? = null,
    val indexInfo: GooglePollenIndexInfoDto? = null,
    val healthRecommendations: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GooglePollenPlantInfoDto(
    val code: String = "",
    val displayName: String = "",
    val inSeason: Boolean? = null,
    val indexInfo: GooglePollenIndexInfoDto? = null
)

@JsonClass(generateAdapter = true)
data class GooglePollenDayInfoDto(
    val date: GooglePollenDateDto = GooglePollenDateDto(),
    val pollenTypeInfo: List<GooglePollenTypeInfoDto> = emptyList(),
    val plantInfo: List<GooglePollenPlantInfoDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GooglePollenForecastResponseDto(
    val regionCode: String? = null,
    val dailyInfo: List<GooglePollenDayInfoDto> = emptyList(),
    val nextPageToken: String? = null
)
