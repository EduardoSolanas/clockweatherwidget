package com.clockweather.app.domain.model

import java.time.LocalDate

data class WeatherData(
    val location: Location,
    val currentWeather: CurrentWeather,
    val hourlyForecasts: List<HourlyForecast>,
    val dailyForecasts: List<DailyForecast>,
    val airQuality: AirQuality? = null
)

fun WeatherData.normalizeDailyConditions(): WeatherData {
    val today = LocalDate.now()
    return copy(
        dailyForecasts = dailyForecasts.map { forecast ->
            if (forecast.date == today) forecast.copy(weatherCondition = currentWeather.weatherCondition)
            else forecast
        }
    )
}

