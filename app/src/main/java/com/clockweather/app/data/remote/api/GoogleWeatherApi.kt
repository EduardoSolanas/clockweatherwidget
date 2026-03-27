package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleWeatherApi {

    /**
     * Current weather conditions for a lat/lon.
     * Docs: https://developers.google.com/maps/documentation/weather/current-conditions
     */
    @GET("v1/currentConditions:lookup")
    suspend fun getCurrentConditions(
        @Query("key") apiKey: String,
        @Query("location.latitude") latitude: Double,
        @Query("location.longitude") longitude: Double,
        @Query("unitsSystem") unitsSystem: String = "METRIC",
        @Query("languageCode") languageCode: String = "en"
    ): GoogleCurrentConditionsDto

    /**
     * Hourly forecast for up to 240 hours.
     */
    @GET("v1/forecast/hours:lookup")
    suspend fun getHourlyForecast(
        @Query("key") apiKey: String,
        @Query("location.latitude") latitude: Double,
        @Query("location.longitude") longitude: Double,
        @Query("pageSize") pageSize: Int = 24,
        @Query("unitsSystem") unitsSystem: String = "METRIC",
        @Query("languageCode") languageCode: String = "en"
    ): GoogleHourlyForecastResponseDto

    /**
     * Daily forecast for up to 10 days.
     */
    @GET("v1/forecast/days:lookup")
    suspend fun getDailyForecast(
        @Query("key") apiKey: String,
        @Query("location.latitude") latitude: Double,
        @Query("location.longitude") longitude: Double,
        @Query("pageSize") pageSize: Int = 7,
        @Query("unitsSystem") unitsSystem: String = "METRIC",
        @Query("languageCode") languageCode: String = "en"
    ): GoogleDailyForecastResponseDto

    companion object {
        const val BASE_URL = "https://weather.googleapis.com/"
    }
}
