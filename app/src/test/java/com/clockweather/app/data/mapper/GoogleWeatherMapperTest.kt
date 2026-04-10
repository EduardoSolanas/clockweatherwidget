package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleTemperatureDto
import com.clockweather.app.data.remote.dto.google.GoogleTimeZoneDto
import com.clockweather.app.domain.model.Location
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.TimeZone

class GoogleWeatherMapperTest {

    private val mapper = GoogleWeatherMapper()

    private lateinit var originalTimeZone: TimeZone

    private val location = Location(
        id = 1L,
        name = "Paris",
        country = "FR",
        latitude = 48.85,
        longitude = 2.35
    )

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `lastUpdated is converted to device local time, not UTC`() {
        // UTC+2 (Europe/Paris during CEST summer time)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))

        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2024-06-15T10:00:00Z",  // UTC 10:00 → local 12:00 in UTC+2
            timeZone = GoogleTimeZoneDto(id = "Europe/Paris"),
            temperature = GoogleTemperatureDto(degrees = 22.0),
            feelsLikeTemperature = GoogleTemperatureDto(degrees = 21.0)
        )

        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = null,
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )

        assertEquals(
            "lastUpdated should be local time (12:00), not UTC (10:00)",
            LocalDateTime.of(2024, 6, 15, 12, 0, 0),
            result.currentWeather.lastUpdated
        )
    }

    @Test
    fun `lastUpdated is correct across DST boundary (UTC+1 to UTC+2)`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))

        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2024-03-31T12:00:00Z",  // UTC 12:00 → local 14:00 (UTC+2 after DST)
            timeZone = GoogleTimeZoneDto(id = "Europe/Paris"),
            temperature = GoogleTemperatureDto(degrees = 15.0),
            feelsLikeTemperature = GoogleTemperatureDto(degrees = 14.0)
        )

        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = null,
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )

        assertEquals(
            "lastUpdated after DST change should be UTC+2 (14:00), not UTC (12:00)",
            LocalDateTime.of(2024, 3, 31, 14, 0, 0),
            result.currentWeather.lastUpdated
        )
    }

    @Test
    fun `lastUpdated is correct for large UTC offset (UTC+12)`() {
        // Reproduces the ~12h discrepancy seen on device:
        // storing UTC time as LocalDateTime and comparing with LocalDateTime.now() (local)
        // produces a gap equal to the full timezone offset
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))  // UTC+12

        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2024-06-15T02:00:00Z",  // UTC 02:00 → local 14:00 (UTC+12)
            timeZone = GoogleTimeZoneDto(id = "Pacific/Auckland"),
            temperature = GoogleTemperatureDto(degrees = 10.0),
            feelsLikeTemperature = GoogleTemperatureDto(degrees = 9.0)
        )

        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = null,
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )

        assertEquals(
            "lastUpdated should be local time 14:00, not UTC 02:00",
            LocalDateTime.of(2024, 6, 15, 14, 0, 0),
            result.currentWeather.lastUpdated
        )
    }
}
