package com.clockweather.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hourly_forecast")
data class HourlyForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: Long,
    val dateTime: String, // ISO-8601
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val dewPoint: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val pressure: Double,
    val windSpeed: Double,
    val windDirectionDegrees: Int,
    val visibility: Double,
    val uvIndex: Double
)

