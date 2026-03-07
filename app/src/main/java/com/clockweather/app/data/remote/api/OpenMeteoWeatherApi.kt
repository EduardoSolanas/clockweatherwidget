package com.clockweather.app.data.remote.api

import com.clockweather.app.data.remote.dto.WeatherResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoWeatherApi {

    @GET("v1/forecast")
    suspend fun getWeatherForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius"
    ): WeatherResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        const val CURRENT_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
                "is_day,precipitation,weather_code,cloud_cover,pressure_msl,surface_pressure," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m"

        const val HOURLY_PARAMS = "temperature_2m,relative_humidity_2m,dew_point_2m," +
                "apparent_temperature,precipitation_probability,weather_code,pressure_msl," +
                "visibility,wind_speed_10m,wind_direction_10m,uv_index,is_day"

        const val DAILY_PARAMS = "weather_code,temperature_2m_max,temperature_2m_min," +
                "apparent_temperature_max,apparent_temperature_min,sunrise,sunset," +
                "daylight_duration,precipitation_sum,precipitation_probability_max," +
                "wind_speed_10m_max,wind_direction_10m_dominant,uv_index_max"
    }
}

