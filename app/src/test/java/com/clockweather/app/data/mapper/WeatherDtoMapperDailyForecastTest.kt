package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.CurrentWeatherDto
import com.clockweather.app.data.remote.dto.DailyWeatherDto
import com.clockweather.app.data.remote.dto.HourlyWeatherDto
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherDtoMapperDailyForecastTest {

    private val mapper = WeatherDtoMapper()

    @Test
    fun `daily forecast uses daytime weather condition from hourly data instead of nighttime`() {
        val location = Location(
            id = 1L,
            name = "Test City",
            country = "Test",
            latitude = 0.0,
            longitude = 0.0
        )

        val response = WeatherResponseDto(
            latitude = 0.0,
            longitude = 0.0,
            elevation = 100.0,
            generationTimeMs = 10.0,
            utcOffsetSeconds = 0,
            timezone = "UTC",
            timezoneAbbreviation = "UTC",
            current = CurrentWeatherDto(
                time = "2026-04-27T12:00:00",
                temperature = 18.0,
                apparentTemperature = 17.0,
                isDay = 1,
                weatherCode = 80, // Code for moderate rain
                windSpeed = 12.0,
                windDirection = 180,
                windGusts = 15.0,
                relativeHumidity = 60,
                pressureMsl = 1013.0,
                surfacePressure = 1012.0,
                cloudCover = 80,
                precipitation = 5.0
            ),
            currentUnits = null,
            hourly = HourlyWeatherDto(
                time = listOf(
                    "2026-04-27T03:00:00", // Night
                    "2026-04-27T12:00:00"  // Day
                ),
                temperature = listOf(12.0, 18.0),
                apparentTemperature = listOf(10.0, 17.0),
                isDay = listOf(0, 1),
                weatherCode = listOf(80, 0), // Night: rain, Day: clear
                windSpeed = listOf(10.0, 12.0),
                windDirection = listOf(180, 180),
                relativeHumidity = listOf(70, 60),
                dewPoint = listOf(6.0, 6.0),
                pressureMsl = listOf(1013.0, 1013.0),
                precipitationProbability = listOf(50, 10),
                visibility = listOf(5000.0, 10000.0),
                uvIndex = listOf(0.0, 3.0)
            ),
            hourlyUnits = null,
            daily = DailyWeatherDto(
                time = listOf("2026-04-27"),
                weatherCode = listOf(80), // Overall day code (rain)
                temperatureMax = listOf(20.0),
                temperatureMin = listOf(10.0),
                apparentTemperatureMax = listOf(18.0),
                apparentTemperatureMin = listOf(8.0),
                sunrise = listOf("2026-04-27T06:30:00"),
                sunset = listOf("2026-04-27T19:45:00"),
                daylightDuration = listOf(47700.0),
                precipitationSum = listOf(5.0),
                precipitationProbabilityMax = listOf(80),
                windSpeedMax = listOf(15.0),
                windDirectionDominant = listOf(180),
                uvIndexMax = listOf(3.0)
            ),
            dailyUnits = null
        )

        val weatherData = mapper.mapToWeatherData(response, location)

        // The daily forecast should use the daytime weather condition (CLEAR_DAY from hourly code 0)
        // NOT the overall daily code 80 (RAIN_MODERATE)
        assertEquals(1, weatherData.dailyForecasts.size)
        assertEquals(WeatherCondition.CLEAR_DAY, weatherData.dailyForecasts[0].weatherCondition)
    }

    @Test
    fun `daily forecast falls back to DTO weather code when no daytime hourly data available`() {
        val location = Location(
            id = 1L,
            name = "Test City",
            country = "Test",
            latitude = 0.0,
            longitude = 0.0
        )

        val response = WeatherResponseDto(
            latitude = 0.0,
            longitude = 0.0,
            elevation = 100.0,
            generationTimeMs = 10.0,
            utcOffsetSeconds = 0,
            timezone = "UTC",
            timezoneAbbreviation = "UTC",
            current = CurrentWeatherDto(
                time = "2026-04-27T03:00:00",
                temperature = 12.0,
                apparentTemperature = 10.0,
                isDay = 0,
                weatherCode = 0,
                windSpeed = 3.0,
                windDirection = 0,
                windGusts = 5.0,
                relativeHumidity = 70,
                pressureMsl = 1013.0,
                surfacePressure = 1012.0,
                cloudCover = 10,
                precipitation = 0.0
            ),
            currentUnits = null,
            hourly = HourlyWeatherDto(
                time = listOf("2026-04-27T03:00:00"), // Only night
                temperature = listOf(12.0),
                apparentTemperature = listOf(10.0),
                isDay = listOf(0),
                weatherCode = listOf(0),
                windSpeed = listOf(3.0),
                windDirection = listOf(0),
                relativeHumidity = listOf(70),
                dewPoint = listOf(6.0),
                pressureMsl = listOf(1013.0),
                precipitationProbability = listOf(0),
                visibility = listOf(10000.0),
                uvIndex = listOf(0.0)
            ),
            hourlyUnits = null,
            daily = DailyWeatherDto(
                time = listOf("2026-04-27"),
                weatherCode = listOf(0), // Clear
                temperatureMax = listOf(20.0),
                temperatureMin = listOf(10.0),
                apparentTemperatureMax = listOf(18.0),
                apparentTemperatureMin = listOf(8.0),
                sunrise = listOf("2026-04-27T06:30:00"),
                sunset = listOf("2026-04-27T19:45:00"),
                daylightDuration = listOf(47700.0),
                precipitationSum = listOf(0.0),
                precipitationProbabilityMax = listOf(0),
                windSpeedMax = listOf(5.0),
                windDirectionDominant = listOf(0),
                uvIndexMax = listOf(4.0)
            ),
            dailyUnits = null
        )

        val weatherData = mapper.mapToWeatherData(response, location)

        // Should fall back to DTO weather code (0 = CLEAR_DAY with isDay=true) since no daytime hourly
        assertEquals(1, weatherData.dailyForecasts.size)
        assertEquals(WeatherCondition.CLEAR_DAY, weatherData.dailyForecasts[0].weatherCondition)
    }
}
