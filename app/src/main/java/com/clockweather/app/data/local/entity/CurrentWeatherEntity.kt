package com.clockweather.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_weather")
data class CurrentWeatherEntity(
    @PrimaryKey val locationId: Long,
    val temperature: Double,
    val feelsLikeTemperature: Double,
    val humidity: Int,
    val dewPoint: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val pressure: Double,
    val windSpeed: Double,
    val windDirectionDegrees: Int,
    val windGusts: Double,
    val visibility: Double,
    val uvIndex: Double,
    val cloudCover: Int,
    val lastUpdated: String, // ISO-8601 string
    // Air Quality (nullable — may not be available)
    val aqCo: Double? = null,
    val aqNo2: Double? = null,
    val aqO3: Double? = null,
    val aqSo2: Double? = null,
    val aqPm25: Double? = null,
    val aqPm10: Double? = null,
    val aqUsEpaIndex: Int? = null,
    val aqGbDefraIndex: Int? = null
)

