package com.clockweather.app.data.remote.dto.openweathermap

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Root response for OpenWeatherMap's One Call 3.0 endpoint.
 * Docs: https://openweathermap.org/api/one-call-3
 */
@JsonClass(generateAdapter = true)
data class OpenWeatherMapOneCallResponseDto(
    val lat: Double,
    val lon: Double,
    val timezone: String?,
    @Json(name = "timezone_offset") val timezoneOffset: Int?,
    val current: OpenWeatherMapCurrentDto,
    val hourly: List<OpenWeatherMapHourlyDto>? = null,
    val daily: List<OpenWeatherMapDailyDto>
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapCurrentDto(
    val dt: Long,
    val sunrise: Long?,
    val sunset: Long?,
    val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    @Json(name = "dew_point") val dewPoint: Double,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int?,
    @Json(name = "wind_speed") val windSpeed: Double,
    @Json(name = "wind_deg") val windDeg: Int,
    @Json(name = "wind_gust") val windGust: Double? = null,
    val weather: List<OpenWeatherMapConditionDto>,
    val rain: OpenWeatherMapPrecipitationDto? = null,
    val snow: OpenWeatherMapPrecipitationDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapHourlyDto(
    val dt: Long,
    val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    @Json(name = "dew_point") val dewPoint: Double,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int?,
    @Json(name = "wind_speed") val windSpeed: Double,
    @Json(name = "wind_deg") val windDeg: Int,
    @Json(name = "wind_gust") val windGust: Double? = null,
    val weather: List<OpenWeatherMapConditionDto>,
    val pop: Double? = null
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapDailyDto(
    val dt: Long,
    val sunrise: Long?,
    val sunset: Long?,
    val temp: OpenWeatherMapDailyTempDto,
    @Json(name = "feels_like") val feelsLike: OpenWeatherMapDailyFeelsLikeDto,
    val pressure: Int,
    val humidity: Int,
    @Json(name = "dew_point") val dewPoint: Double,
    @Json(name = "wind_speed") val windSpeed: Double,
    @Json(name = "wind_deg") val windDeg: Int,
    @Json(name = "wind_gust") val windGust: Double? = null,
    val weather: List<OpenWeatherMapConditionDto>,
    val clouds: Int,
    val pop: Double? = null,
    val rain: Double? = null,
    val snow: Double? = null,
    val uvi: Double
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapDailyTempDto(
    val day: Double,
    val min: Double,
    val max: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapDailyFeelsLikeDto(
    val day: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapConditionDto(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMapPrecipitationDto(
    @Json(name = "1h") val oneHour: Double? = null
)
