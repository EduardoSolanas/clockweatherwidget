package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.NominatimReverseGeocodingResponseDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface NominatimReverseGeocodingApi {

    @Headers("User-Agent: clockweatherwidget/1.0 (reverse-geocoding)")
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 16
    ): NominatimReverseGeocodingResponseDto

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"
    }
}

