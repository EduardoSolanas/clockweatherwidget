package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

class GoogleWeatherMapper @Inject constructor() {

    fun mapToWeatherData(
        current: GoogleCurrentConditionsDto,
        hourly: GoogleHourlyForecastResponseDto?,
        daily: GoogleDailyForecastResponseDto,
        location: Location
    ): WeatherData {
        val timezone = current.timeZone?.id ?: "UTC"
        return WeatherData(
            location = location,
            currentWeather = mapCurrent(current),
            hourlyForecasts = hourly?.forecastHours?.map { mapHourly(it) } ?: emptyList(),
            dailyForecasts = daily.forecastDays.map { mapDaily(it, timezone) },
            airQuality = null  // Google Weather API does not provide AQI data
        )
    }

    private fun mapCurrent(dto: GoogleCurrentConditionsDto): CurrentWeather {
        val windDeg = dto.wind?.direction?.degrees?.toInt() ?: 0
        val lastUpdated = runCatching {
            Instant.parse(dto.currentTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }.getOrElse { LocalDateTime.now() }

        return CurrentWeather(
            temperature = dto.temperature.degrees,
            feelsLikeTemperature = dto.feelsLikeTemperature.degrees,
            humidity = dto.humidity,
            dewPoint = dto.dewPoint?.degrees ?: 0.0,
            precipitation = dto.precipitation?.qpf?.quantity ?: 0.0,
            precipitationProbability = dto.precipitation?.probability?.percent ?: 0,
            weatherCondition = WeatherCondition.fromGoogleWeatherType(
                dto.weatherCondition.type, dto.isDaytime
            ),
            isDay = dto.isDaytime,
            pressure = dto.pressure?.meanSeaLevelMillibars ?: 1013.25,
            windSpeed = dto.wind?.speed?.value ?: 0.0,
            windDirection = WindDirection.fromDegrees(windDeg),
            windDirectionDegrees = windDeg,
            windGusts = dto.wind?.gust?.value ?: 0.0,
            visibility = dto.visibility?.distance ?: 10000.0,
            uvIndex = dto.uvIndex.toDouble(),
            cloudCover = dto.cloudCover,
            lastUpdated = lastUpdated
        )
    }

    private fun mapHourly(dto: GoogleHourlyForecastDto): HourlyForecast {
        val dt = dto.displayDateTime
        val dateTime = runCatching {
            LocalDateTime.of(dt.year, dt.month, dt.day, dt.hours, dt.minutes, dt.seconds)
        }.getOrElse { LocalDateTime.now() }

        val windDeg = dto.wind?.direction?.degrees?.toInt() ?: 0

        return HourlyForecast(
            dateTime = dateTime,
            temperature = dto.temperature.degrees,
            feelsLike = dto.feelsLikeTemperature?.degrees ?: dto.temperature.degrees,
            humidity = dto.humidity,
            dewPoint = dto.dewPoint?.degrees ?: 0.0,
            precipitationProbability = dto.precipitation?.probability?.percent ?: 0,
            weatherCondition = WeatherCondition.fromGoogleWeatherType(
                dto.weatherCondition.type, dto.isDaytime
            ),
            isDay = dto.isDaytime,
            pressure = dto.pressure?.meanSeaLevelMillibars ?: 1013.25,
            windSpeed = dto.wind?.speed?.value ?: 0.0,
            windDirection = WindDirection.fromDegrees(windDeg),
            windDirectionDegrees = windDeg,
            visibility = dto.visibility?.distance ?: 10000.0,
            uvIndex = dto.uvIndex?.toDouble() ?: 0.0
        )
    }

    private fun mapDaily(dto: GoogleDailyForecastDto, timezone: String): DailyForecast {
        val d = dto.displayDate
        val date = runCatching { LocalDate.of(d.year, d.month, d.day) }.getOrElse { LocalDate.now() }

        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneOffset.UTC }
        val sunrise = parseUtcToLocalTime(dto.sunEvents?.sunriseTime, zone) ?: LocalTime.of(6, 0)
        val sunset  = parseUtcToLocalTime(dto.sunEvents?.sunsetTime,  zone) ?: LocalTime.of(18, 0)

        val daylightSeconds = if (sunset.isAfter(sunrise))
            (sunset.toSecondOfDay() - sunrise.toSecondOfDay()).toDouble()
        else 43200.0

        val windDeg = dto.wind?.direction?.degrees?.toInt() ?: 0
        val humidityMin = dto.humidity?.min ?: 50
        val humidityMax = dto.humidity?.max ?: 50

        return DailyForecast(
            date = date,
            weatherCondition = WeatherCondition.fromGoogleWeatherType(
                dto.weatherCondition.type, isDay = true
            ),
            temperatureMax = dto.maxTemperature.degrees,
            temperatureMin = dto.minTemperature.degrees,
            feelsLikeMax = dto.feelsLikeMaxTemperature?.degrees ?: dto.maxTemperature.degrees,
            feelsLikeMin = dto.feelsLikeMinTemperature?.degrees ?: dto.minTemperature.degrees,
            sunrise = sunrise,
            sunset = sunset,
            daylightDurationSeconds = daylightSeconds,
            precipitationSum = dto.precipitation?.qpf?.quantity ?: 0.0,
            precipitationProbability = maxOf(
                dto.daytimeForecast?.precipitation?.probability?.percent ?: 0,
                dto.nighttimeForecast?.precipitation?.probability?.percent ?: 0
            ),
            windSpeedMax = dto.wind?.maxSpeed?.value ?: 0.0,
            windDirectionDominant = WindDirection.fromDegrees(windDeg),
            windDirectionDegrees = windDeg,
            uvIndexMax = dto.uvIndex?.toDouble() ?: 0.0,
            averageHumidity = (humidityMin + humidityMax) / 2,
            averagePressure = 1013.25  // Google daily forecast does not include pressure
        )
    }

    private fun parseUtcToLocalTime(utcTimestamp: String?, zone: ZoneId): LocalTime? {
        utcTimestamp ?: return null
        return runCatching {
            Instant.parse(utcTimestamp).atZone(zone).toLocalTime()
        }.getOrNull()
    }
}
