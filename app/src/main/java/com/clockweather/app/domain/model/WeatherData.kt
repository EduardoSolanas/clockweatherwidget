package com.clockweather.app.domain.model

data class WeatherData(
    val location: Location,
    val currentWeather: CurrentWeather,
    val hourlyForecasts: List<HourlyForecast>,
    val dailyForecasts: List<DailyForecast>,
    val airQuality: AirQuality? = null
)

