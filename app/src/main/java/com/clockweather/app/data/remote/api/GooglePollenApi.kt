package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.google.GooglePollenForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface GooglePollenApi {

    /**
     * Daily pollen forecast for up to 5 days.
     * Docs: https://developers.google.com/maps/documentation/pollen/forecast
     */
    @GET("v1/forecast:lookup")
    suspend fun getPollenForecast(
        @Query("key") apiKey: String,
        @Query("location.latitude") latitude: Double,
        @Query("location.longitude") longitude: Double,
        @Query("days") days: Int = 5,
        @Query("languageCode") languageCode: String = "en",
        @Query("plantsDescription") plantsDescription: Boolean = false
    ): GooglePollenForecastResponseDto

    companion object {
        const val BASE_URL = "https://pollen.googleapis.com/"
    }
}
