package com.clockweather.app.data.remote.dto.google

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Shared primitives ────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class GoogleTemperatureDto(
    val degrees: Double = 0.0,
    val unit: String = "CELSIUS"
)

@JsonClass(generateAdapter = true)
data class GoogleSpeedDto(
    val value: Double = 0.0,
    val unit: String = "KILOMETERS_PER_HOUR"
)

@JsonClass(generateAdapter = true)
data class GoogleDistanceDto(
    val distance: Double = 10000.0,
    val unit: String = "METERS"
)

@JsonClass(generateAdapter = true)
data class GoogleQuantityDto(
    val quantity: Double = 0.0,
    val unit: String = "MILLIMETERS"
)

@JsonClass(generateAdapter = true)
data class GooglePressureDto(
    val meanSeaLevelMillibars: Double = 1013.25
)

@JsonClass(generateAdapter = true)
data class GoogleTextDto(
    val text: String = "",
    val languageCode: String = "en"
)

@JsonClass(generateAdapter = true)
data class GoogleConditionTypeDto(
    val type: String = "CLEAR",
    val description: GoogleTextDto? = null,
    val iconBaseUri: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleWindDirectionDto(
    val degrees: Double = 0.0,
    val cardinal: String = "NORTH"
)

@JsonClass(generateAdapter = true)
data class GoogleWindDto(
    val direction: GoogleWindDirectionDto? = null,
    val speed: GoogleSpeedDto? = null,
    val gust: GoogleSpeedDto? = null
)

@JsonClass(generateAdapter = true)
data class GooglePrecipProbabilityDto(
    val percent: Int = 0,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class GooglePrecipitationDto(
    val probability: GooglePrecipProbabilityDto? = null,
    val qpf: GoogleQuantityDto? = null,
    val snowQpf: GoogleQuantityDto? = null
)

@JsonClass(generateAdapter = true)
data class GoogleTimeZoneDto(
    val id: String = "UTC"
)

@JsonClass(generateAdapter = true)
data class GoogleIntervalDto(
    val startTime: String = "",
    val endTime: String = ""
)

// ─── Current Conditions ───────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class GoogleCurrentConditionsDto(
    val currentTime: String = "",
    val timeZone: GoogleTimeZoneDto? = null,
    val isDaytime: Boolean = true,
    val weatherCondition: GoogleConditionTypeDto = GoogleConditionTypeDto(),
    val temperature: GoogleTemperatureDto = GoogleTemperatureDto(),
    val feelsLikeTemperature: GoogleTemperatureDto = GoogleTemperatureDto(),
    val dewPoint: GoogleTemperatureDto? = null,
    val heatIndex: GoogleTemperatureDto? = null,
    val windChill: GoogleTemperatureDto? = null,
    val humidity: Int = 0,
    val wind: GoogleWindDto? = null,
    val visibility: GoogleDistanceDto? = null,
    val cloudCover: Int = 0,
    val pressure: GooglePressureDto? = null,
    val precipitation: GooglePrecipitationDto? = null,
    val uvIndex: Int = 0,
    val thunderstormProbability: Int? = null
)

// ─── Hourly Forecast ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class GoogleDisplayDateTimeDto(
    val year: Int = 2024,
    val month: Int = 1,
    val day: Int = 1,
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val nanos: Int = 0,
    val utcOffset: String = "+00:00"
)

@JsonClass(generateAdapter = true)
data class GoogleHourlyForecastDto(
    val interval: GoogleIntervalDto? = null,
    val displayDateTime: GoogleDisplayDateTimeDto = GoogleDisplayDateTimeDto(),
    val isDaytime: Boolean = true,
    val weatherCondition: GoogleConditionTypeDto = GoogleConditionTypeDto(),
    val temperature: GoogleTemperatureDto = GoogleTemperatureDto(),
    val feelsLikeTemperature: GoogleTemperatureDto? = null,
    val dewPoint: GoogleTemperatureDto? = null,
    val humidity: Int = 0,
    val wind: GoogleWindDto? = null,
    val visibility: GoogleDistanceDto? = null,
    val cloudCover: Int? = null,
    val pressure: GooglePressureDto? = null,
    val precipitation: GooglePrecipitationDto? = null,
    val uvIndex: Int? = null,
    val thunderstormProbability: Int? = null
)

@JsonClass(generateAdapter = true)
data class GoogleHourlyForecastResponseDto(
    val forecastHours: List<GoogleHourlyForecastDto> = emptyList()
)

// ─── Daily Forecast ───────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class GoogleDisplayDateDto(
    val year: Int = 2024,
    val month: Int = 1,
    val day: Int = 1
)

@JsonClass(generateAdapter = true)
data class GoogleSunEventsDto(
    val sunriseTime: String? = null,
    val sunsetTime: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDailyWindDto(
    val maxSpeed: GoogleSpeedDto? = null,
    val direction: GoogleWindDirectionDto? = null,
    val gust: GoogleSpeedDto? = null
)

@JsonClass(generateAdapter = true)
data class GoogleHumidityRangeDto(
    val min: Int? = null,
    val max: Int? = null
)

@JsonClass(generateAdapter = true)
data class GooglePartialDayForecastDto(
    val weatherCondition: GoogleConditionTypeDto? = null,
    val cloudCover: Int? = null,
    val precipitation: GooglePrecipitationDto? = null,
    val wind: GoogleDailyWindDto? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDailyForecastDto(
    val interval: GoogleIntervalDto? = null,
    val displayDate: GoogleDisplayDateDto = GoogleDisplayDateDto(),
    val sunEvents: GoogleSunEventsDto? = null,
    val maxTemperature: GoogleTemperatureDto = GoogleTemperatureDto(),
    val minTemperature: GoogleTemperatureDto = GoogleTemperatureDto(),
    val feelsLikeMaxTemperature: GoogleTemperatureDto? = null,
    val feelsLikeMinTemperature: GoogleTemperatureDto? = null,
    val weatherCondition: GoogleConditionTypeDto = GoogleConditionTypeDto(),
    val precipitation: GooglePrecipitationDto? = null,
    val wind: GoogleDailyWindDto? = null,
    val uvIndex: Int? = null,
    val humidity: GoogleHumidityRangeDto? = null,
    val daytimeForecast: GooglePartialDayForecastDto? = null,
    val nighttimeForecast: GooglePartialDayForecastDto? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDailyForecastResponseDto(
    val forecastDays: List<GoogleDailyForecastDto> = emptyList()
)
