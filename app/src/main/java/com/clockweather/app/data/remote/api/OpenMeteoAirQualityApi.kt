package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoAirQualityApi {

    /**
     * Hourly Air Quality and Pollen forecast.
     * Docs: https://open-meteo.com/en/docs/air-quality-api
     */
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,us_aqi,european_aqi,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): OpenMeteoAirQualityResponseDto

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
    }
}
