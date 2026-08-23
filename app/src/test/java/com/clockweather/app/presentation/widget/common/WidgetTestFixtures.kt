package com.clockweather.app.presentation.widget.common

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import com.clockweather.app.presentation.settings.SettingsViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Shared real-object fixtures for widget tests. No mocks — these are the actual domain types. */
object WidgetTestFixtures {

    val london = Location(
        id = 1L,
        name = "London",
        country = "UK",
        latitude = 51.5072,
        longitude = -0.1276,
        timezone = "auto",
    )

    fun weatherData(
        reference: LocalDateTime,
        lastUpdated: LocalDateTime = reference.minusMinutes(5),
        hourlyForecasts: List<HourlyForecast> = hourlyForecastsFrom(reference, count = 24),
        dailyForecasts: List<DailyForecast> = dailyForecastsFrom(reference.toLocalDate(), count = 7),
    ) = WeatherData(
        location = london,
        currentWeather = CurrentWeather(
            temperature = 15.0,
            feelsLikeTemperature = 15.0,
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
            lastUpdated = lastUpdated,
        ),
        hourlyForecasts = hourlyForecasts,
        dailyForecasts = dailyForecasts,
    )

    fun snapshot(
        weather: WeatherData?,
        refreshIntervalMinutes: Int? = null,
        iconStyle: String? = null,
    ): WidgetRenderSnapshot {
        val prefs = mutablePreferencesOf().apply {
            refreshIntervalMinutes?.let { this[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL] = it }
            iconStyle?.let { this[SettingsViewModel.KEY_WEATHER_ICON_STYLE] = it }
        }
        return WidgetRenderSnapshot(prefs = prefs, location = london, weather = weather)
    }

    fun hourlyForecastsFrom(start: LocalDateTime, count: Int): List<HourlyForecast> {
        val firstHour = start.withMinute(0).withSecond(0).withNano(0)
        return (0 until count).map { offset ->
            HourlyForecast(
                dateTime = firstHour.plusHours(offset.toLong()),
                temperature = 15.0 + offset,
                feelsLike = 15.0 + offset,
                humidity = 60,
                dewPoint = 9.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 5.0,
            )
        }
    }

    fun dailyForecastsFrom(start: LocalDate, count: Int): List<DailyForecast> =
        (0 until count).map { offset ->
            DailyForecast(
                date = start.plusDays(offset.toLong()),
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                temperatureMax = 20.0,
                temperatureMin = 11.0,
                feelsLikeMax = 20.0,
                feelsLikeMin = 11.0,
                sunrise = LocalTime.of(6, 0),
                sunset = LocalTime.of(19, 0),
                daylightDurationSeconds = 36_000.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 10.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 5.0,
                averageHumidity = 60,
                averagePressure = 1012.0,
            )
        }
}
