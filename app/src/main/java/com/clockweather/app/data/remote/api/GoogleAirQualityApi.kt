package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.google.GoogleAirQualityRequestDto
import com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GoogleAirQualityApi {

    /**
     * Current air quality conditions for a lat/lon.
     * Docs: https://developers.google.com/maps/documentation/air-quality/current-conditions
     */
    @POST("v1/currentConditions:lookup")
    suspend fun getCurrentConditions(
        @Query("key") apiKey: String,
        @Body body: GoogleAirQualityRequestDto
    ): GoogleAirQualityResponseDto

    companion object {
        const val BASE_URL = "https://airquality.googleapis.com/"
    }
}
