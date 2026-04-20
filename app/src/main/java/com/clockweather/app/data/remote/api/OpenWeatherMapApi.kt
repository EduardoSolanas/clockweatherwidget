package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapOneCallResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherMapApi {

    /**
     * One Call 3.0 — returns current conditions, hourly (48h) and daily (8 days).
     * Docs: https://openweathermap.org/api/one-call-3
     */
    @GET("data/3.0/onecall")
    suspend fun getOneCall(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("exclude") exclude: String = "minutely,alerts"
    ): OpenWeatherMapOneCallResponseDto

    companion object {
        const val BASE_URL = "https://api.openweathermap.org/"
    }
}
