package com.clockweather.app.presentation.widget.common

import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WindDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LocalDayNightDisplayMapperTest {

    private val baseForecast = DailyForecast(
        date = LocalDate.now(),
        weatherCondition = WeatherCondition.CLEAR_DAY,
        temperatureMax = 20.0,
        temperatureMin = 10.0,
        feelsLikeMax = 20.0,
        feelsLikeMin = 10.0,
        sunrise = LocalTime.of(6, 30),
        sunset = LocalTime.of(19, 45),
        daylightDurationSeconds = 47700.0,
        precipitationSum = 0.0,
        precipitationProbability = 0,
        windSpeedMax = 10.0,
        windDirectionDominant = WindDirection.N,
        windDirectionDegrees = 0,
        uvIndexMax = 5.0,
        averageHumidity = 50,
        averagePressure = 1013.0
    )

    @Test
    fun `daytime before sunset converts clear night to clear day`() {
        val result = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.CLEAR_NIGHT,
            currentTime = LocalTime.of(12, 0),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.CLEAR_DAY, result)
    }

    @Test
    fun `nighttime after sunset converts clear day to clear night`() {
        val result = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.CLEAR_DAY,
            currentTime = LocalTime.of(21, 0),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.CLEAR_NIGHT, result)
    }

    @Test
    fun `nighttime before sunrise converts partly cloudy day to partly cloudy night`() {
        val result = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.PARTLY_CLOUDY_DAY,
            currentTime = LocalTime.of(4, 0),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.PARTLY_CLOUDY_NIGHT, result)
    }

    @Test
    fun `exact sunrise is day`() {
        val result = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.MAINLY_CLEAR_NIGHT,
            currentTime = LocalTime.of(6, 30),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.MAINLY_CLEAR_DAY, result)
    }

    @Test
    fun `exact sunset is night`() {
        val result = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.MAINLY_CLEAR_DAY,
            currentTime = LocalTime.of(19, 45),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.MAINLY_CLEAR_NIGHT, result)
    }

    @Test
    fun `non day-night variant conditions remain untouched`() {
        val rainyNight = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.RAIN_HEAVY,
            currentTime = LocalTime.of(23, 0),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.RAIN_HEAVY, rainyNight)

        val snowyDay = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.SNOW_MODERATE,
            currentTime = LocalTime.of(12, 0),
            todayForecast = baseForecast
        )
        assertEquals(WeatherCondition.SNOW_MODERATE, snowyDay)
    }

    @Test
    fun `fallback window used when today forecast is null`() {
        val noon = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.CLEAR_NIGHT,
            currentTime = LocalTime.of(12, 0),
            todayForecast = null
        )
        assertEquals(WeatherCondition.CLEAR_DAY, noon)

        val midnight = LocalDayNightDisplayMapper.mapCondition(
            condition = WeatherCondition.CLEAR_DAY,
            currentTime = LocalTime.of(0, 30),
            todayForecast = null
        )
        assertEquals(WeatherCondition.CLEAR_NIGHT, midnight)
    }
}
