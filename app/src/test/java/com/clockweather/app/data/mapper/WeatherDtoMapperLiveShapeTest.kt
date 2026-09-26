package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityResponseDto
import com.clockweather.app.domain.model.Location
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Payloads shaped like Open-Meteo's real responses, parsed through Moshi. */
class WeatherDtoMapperLiveShapeTest {

    private val mapper = WeatherDtoMapper()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val location = Location(id = 1L, name = "London", country = "GB", latitude = 51.54, longitude = -0.10)

    // A 16-day request ends past the model horizon: Open-Meteo sends null for the last day
    // and the last hours rather than omitting them.
    private val forecastJson = """
        {"latitude": 51.54, "longitude": -0.1, "elevation": 30.0, "generationtime_ms": 1.0,
         "utc_offset_seconds": 3600, "timezone": "Europe/London", "timezone_abbreviation": "BST",
         "current": {"time": "2026-09-26T21:30", "temperature_2m": 18.4, "relative_humidity_2m": 43,
           "apparent_temperature": 15.9, "is_day": 0, "precipitation": 0.0, "weather_code": 3,
           "cloud_cover": 100, "pressure_msl": 1022.2, "surface_pressure": 1018.1,
           "wind_speed_10m": 9.4, "wind_direction_10m": 208, "wind_gusts_10m": 20.2,
           "dew_point_2m": 5.6, "visibility": 19380.0, "uv_index": 0.0},
         "hourly": {"time": ["2026-10-11T21:00", "2026-10-11T22:00", "2026-10-11T23:00"],
           "temperature_2m": [12.0, 11.5, null], "relative_humidity_2m": [80, 82, null],
           "dew_point_2m": [8.0, 8.1, null], "apparent_temperature": [10.0, 9.5, null],
           "precipitation_probability": [null, null, null], "weather_code": [3, null, null],
           "pressure_msl": [1015.0, 1015.2, null], "visibility": [24000.0, null, null],
           "wind_speed_10m": [9.0, 8.0, null], "wind_direction_10m": [200, 210, null],
           "uv_index": [0.0, 0.0, null], "is_day": [0, 0, 0]},
         "daily": {"time": ["2026-10-10", "2026-10-11"],
           "weather_code": [51, null], "temperature_2m_max": [16.0, null],
           "temperature_2m_min": [9.0, null], "apparent_temperature_max": [14.0, null],
           "apparent_temperature_min": [7.0, null],
           "sunrise": ["2026-10-10T07:12", "2026-10-11T07:14"],
           "sunset": ["2026-10-10T18:20", "2026-10-11T18:18"],
           "daylight_duration": [40000.0, 39700.0], "precipitation_sum": [1.2, null],
           "precipitation_probability_max": [40, null], "wind_speed_10m_max": [18.0, null],
           "wind_direction_10m_dominant": [220, null], "uv_index_max": [2.0, null]}}
    """.trimIndent()

    @Test
    fun `forecast tail without values is dropped instead of failing the refresh`() {
        val response = moshi.adapter(WeatherResponseDto::class.java).fromJson(forecastJson)!!

        val data = mapper.mapToWeatherData(response, location)

        assertEquals(listOf(LocalDate.of(2026, 10, 10)), data.dailyForecasts.map { it.date })
        assertEquals(listOf(LocalDateTime.of(2026, 10, 11, 21, 0)), data.hourlyForecasts.map { it.dateTime })
    }

    @Test
    fun `air quality reports the current hour on the UK DAQI scale`() {
        val response = moshi.adapter(WeatherResponseDto::class.java).fromJson(forecastJson)!!
        // Values from a live London response: the European AQI (0-100+) sits around 60
        // while every pollutant is in DAQI's Low band.
        val airQuality = moshi.adapter(OpenMeteoAirQualityResponseDto::class.java).fromJson(
            """
            {"latitude": 51.54, "longitude": -0.1, "timezone": "Europe/London",
             "hourly": {"time": ["2026-09-26T20:00", "2026-09-26T21:00", "2026-09-26T22:00"],
               "pm2_5": [19.0, 7.5, 12.0], "pm10": [30.0, 14.0, 20.0],
               "nitrogen_dioxide": [40.0, 30.0, 50.0], "ozone": [50.0, 40.0, 90.0],
               "sulphur_dioxide": [2.0, 2.0, 2.0], "carbon_monoxide": [200.0, 180.0, 210.0],
               "us_aqi": [56, 32, 40], "european_aqi": [62, 60, 50]}}
            """.trimIndent()
        )!!

        val aq = mapper.mapToWeatherData(
            response, location, airQuality,
            now = Instant.parse("2026-09-26T20:30:00Z") // 21:30 in London
        ).airQuality!!

        assertEquals(7.5, aq.pm25, 0.001)
        assertEquals(14.0, aq.pm10, 0.001)
        assertEquals(40.0, aq.o3, 0.001)
        assertEquals(1, aq.usEpaIndex)
        assertEquals(2, aq.gbDefraIndex) // ozone 40 µg/m³ is DAQI 2; the rest are 1
    }
}
