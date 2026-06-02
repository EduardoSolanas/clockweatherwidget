package com.clockweather.app.presentation.detail.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CurrentWeatherCardTest {

    @Test
    fun `resolveForecastIsToday returns true when forecast date matches today`() {
        val today = LocalDate.of(2026, 4, 10)
        assertTrue(resolveForecastIsToday(today, today))
    }

    @Test
    fun `resolveForecastIsToday returns false for yesterday`() {
        val today = LocalDate.of(2026, 4, 10)
        assertFalse(resolveForecastIsToday(today.minusDays(1), today))
    }

    @Test
    fun `resolveForecastIsToday returns false for tomorrow`() {
        val today = LocalDate.of(2026, 4, 10)
        assertFalse(resolveForecastIsToday(today.plusDays(1), today))
    }

    @Test
    fun `resolveForecastIsToday catches stale data bug - index 0 was yesterday`() {
        // Core regression: isToday = index == 0 would return true here, but
        // resolveForecastIsToday correctly returns false when first forecast is stale
        val today = LocalDate.of(2026, 4, 10)
        val staleFirstForecastDate = today.minusDays(1)  // yesterday's cached data
        assertFalse(resolveForecastIsToday(staleFirstForecastDate, today))
    }

    @Test
    fun `unselected forecast day text stays light on dark capsule`() {
        val colors = forecastDayTextColors(isSelected = false)

        assertEquals(Color.White, colors.primary)
        assertEquals(Color.White.copy(alpha = 0.76f), colors.secondary)
    }

    @Test
    fun `compact forecast text gets smaller on narrow columns`() {
        val narrow = forecastDayCompactTextSizes(columnWidthDp = 44f, densityDpi = 440)
        val wide = forecastDayCompactTextSizes(columnWidthDp = 52f, densityDpi = 440)

        assertTrue(narrow.highSp < wide.highSp)
        assertTrue(narrow.windSp < wide.windSp)
    }

    @Test
    fun `compact forecast text gets smaller on lower density devices`() {
        val lowDensity = forecastDayCompactTextSizes(columnWidthDp = 52f, densityDpi = 280)
        val highDensity = forecastDayCompactTextSizes(columnWidthDp = 52f, densityDpi = 440)

        assertTrue(lowDensity.highSp < highDensity.highSp)
        assertTrue(lowDensity.daySp < highDensity.daySp)
    }

    @Test
    fun `hero current temperature display uses current hour forecast`() {
        val reference = LocalDateTime.of(2026, 4, 3, 10, 42)
        val weatherData = sampleWeatherData(
            hourlyForecasts = listOf(
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 9, 0), 14.0),
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 10, 0), 18.0),
                sampleHourlyForecast(LocalDateTime.of(2026, 4, 3, 11, 0), 19.0),
            )
        )

        assertEquals(
            "18\u00B0C",
            currentTemperatureDisplay(weatherData, TemperatureUnit.CELSIUS, reference)
        )
    }

    @Test
    fun `weather page current display uses current hour readings when daily forecast differs`() {
        val reference = LocalDateTime.of(2026, 4, 3, 10, 42)
        val weatherData = sampleWeatherData(
            hourlyForecasts = listOf(
                sampleHourlyForecast(
                    dateTime = LocalDateTime.of(2026, 4, 3, 10, 0),
                    temperature = 18.0,
                    feelsLike = 17.0,
                    humidity = 74,
                    precipitationProbability = 45,
                    weatherCondition = WeatherCondition.RAIN_SLIGHT,
                    windSpeed = 22.0,
                    windDirection = WindDirection.SW,
                    windDirectionDegrees = 225,
                    uvIndex = 2.0,
                ),
            ),
            dailyForecasts = listOf(
                sampleDailyForecast(
                    date = LocalDate.of(2026, 4, 3),
                    weatherCondition = WeatherCondition.CLEAR_DAY,
                    temperatureMax = 24.0,
                    temperatureMin = 9.0,
                    precipitationProbability = 0,
                ),
            ),
        )

        val current = currentWeatherForDisplay(weatherData, reference)

        assertEquals(18.0, current.temperature, 0.0)
        assertEquals(17.0, current.feelsLikeTemperature, 0.0)
        assertEquals(74, current.humidity)
        assertEquals(45, current.precipitationProbability)
        assertEquals(WeatherCondition.RAIN_SLIGHT, current.weatherCondition)
        assertEquals(22.0, current.windSpeed, 0.0)
        assertEquals(WindDirection.SW, current.windDirection)
        assertEquals(225, current.windDirectionDegrees)
        assertEquals(2.0, current.uvIndex, 0.0)
    }

    private fun sampleWeatherData(
        currentTemperature: Double = 17.0,
        hourlyForecasts: List<HourlyForecast> = emptyList(),
        dailyForecasts: List<DailyForecast> = emptyList(),
    ) = WeatherData(
        location = Location(
            id = 1L,
            name = "London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
        ),
        currentWeather = CurrentWeather(
            temperature = currentTemperature,
            feelsLikeTemperature = currentTemperature,
            humidity = 60,
            dewPoint = 9.0,
            precipitation = 0.0,
            precipitationProbability = 0,
            weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
            isDay = true,
            pressure = 1012.0,
            windSpeed = 10.0,
            windDirection = WindDirection.N,
            windDirectionDegrees = 0,
            windGusts = 12.0,
            visibility = 10_000.0,
            uvIndex = 5.0,
            cloudCover = 30,
            lastUpdated = LocalDateTime.of(2026, 4, 3, 10, 15),
        ),
        hourlyForecasts = hourlyForecasts,
        dailyForecasts = dailyForecasts,
    )

    private fun sampleHourlyForecast(
        dateTime: LocalDateTime,
        temperature: Double,
        feelsLike: Double = temperature,
        humidity: Int = 60,
        precipitationProbability: Int = 0,
        weatherCondition: WeatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
        windSpeed: Double = 10.0,
        windDirection: WindDirection = WindDirection.N,
        windDirectionDegrees: Int = 0,
        uvIndex: Double = 5.0,
    ) = HourlyForecast(
        dateTime = dateTime,
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        dewPoint = 9.0,
        precipitationProbability = precipitationProbability,
        weatherCondition = weatherCondition,
        isDay = true,
        pressure = 1012.0,
        windSpeed = windSpeed,
        windDirection = windDirection,
        windDirectionDegrees = windDirectionDegrees,
        visibility = 10_000.0,
        uvIndex = uvIndex,
    )

    private fun sampleDailyForecast(
        date: LocalDate,
        weatherCondition: WeatherCondition,
        temperatureMax: Double,
        temperatureMin: Double,
        precipitationProbability: Int,
    ) = DailyForecast(
        date = date,
        weatherCondition = weatherCondition,
        temperatureMax = temperatureMax,
        temperatureMin = temperatureMin,
        feelsLikeMax = temperatureMax,
        feelsLikeMin = temperatureMin,
        sunrise = LocalTime.of(6, 0),
        sunset = LocalTime.of(19, 0),
        daylightDurationSeconds = 36000.0,
        precipitationSum = 0.0,
        precipitationProbability = precipitationProbability,
        windSpeedMax = 10.0,
        windDirectionDominant = WindDirection.N,
        windDirectionDegrees = 0,
        uvIndexMax = 5.0,
        averageHumidity = 60,
        averagePressure = 1012.0,
    )
}
