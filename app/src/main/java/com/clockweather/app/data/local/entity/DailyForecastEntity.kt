package com.clockweather.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_forecast")
data class DailyForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: Long,
    val date: String, // ISO-8601 date
    val weatherCode: Int,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val feelsLikeMax: Double,
    val feelsLikeMin: Double,
    val sunrise: String, // HH:mm
    val sunset: String,  // HH:mm
    val daylightDurationSeconds: Double,
    val precipitationSum: Double,
    val precipitationProbability: Int,
    val windSpeedMax: Double,
    val windDirectionDegrees: Int,
    val uvIndexMax: Double,
    val averageHumidity: Int,
    val averagePressure: Double
)

