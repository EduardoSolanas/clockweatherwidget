package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    /**
     * Forecast endpoint — returns current + hourly + N days daily.
     * Docs: https://www.weatherapi.com/docs/
     */
    @GET("v1/forecast.json")
    suspend fun getForecast(
        @Query("key") apiKey: String,
        @Query("q") query: String,           // "lat,lon" or city name
        @Query("days") days: Int = 7,
        @Query("aqi") aqi: String = "yes",
        @Query("alerts") alerts: String = "no"
    ): WeatherApiResponseDto

    companion object {
        const val BASE_URL = "https://api.weatherapi.com/"
    }
}

