package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapDailyDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapHourlyDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapOneCallResponseDto
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

class OpenWeatherMapMapper @Inject constructor() {

    fun mapToWeatherData(
        response: OpenWeatherMapOneCallResponseDto,
        location: Location
    ): WeatherData {
        val zoneId = zoneId(response)
        val hourlyForecasts = response.hourly.orEmpty().map { mapHourly(it, zoneId) }
        return WeatherData(
            location = location,
            currentWeather = mapCurrent(response, zoneId),
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = response.daily.map { mapDaily(it, zoneId) },
            airQuality = null
        )
    }

    private fun mapCurrent(
        response: OpenWeatherMapOneCallResponseDto,
        zoneId: ZoneId
    ): CurrentWeather {
        val current = response.current
        val isDay = currentIsDay(current.dt, current.sunrise, current.sunset, current.weather.firstOrNull()?.icon)

        return CurrentWeather(
            temperature = current.temp,
            feelsLikeTemperature = current.feelsLike,
            humidity = current.humidity,
            dewPoint = current.dewPoint,
            precipitation = (current.rain?.oneHour ?: 0.0) + (current.snow?.oneHour ?: 0.0),
            precipitationProbability = 0,
            weatherCondition = WeatherCondition.fromOpenWeatherMapId(
                current.weather.firstOrNull()?.id ?: 800,
                isDay = isDay
            ),
            isDay = isDay,
            pressure = current.pressure.toDouble(),
            windSpeed = current.windSpeed,
            windDirection = WindDirection.fromDegrees(current.windDeg),
            windDirectionDegrees = current.windDeg,
            windGusts = current.windGust ?: current.windSpeed,
            visibility = (current.visibility ?: 10000).toDouble(),
            uvIndex = current.uvi,
            cloudCover = current.clouds,
            lastUpdated = toLocalDateTime(current.dt, zoneId)
        )
    }

    private fun mapHourly(dto: OpenWeatherMapHourlyDto, zoneId: ZoneId): HourlyForecast {
        val isDay = iconIndicatesDay(dto.weather.firstOrNull()?.icon)
        return HourlyForecast(
            dateTime = toLocalDateTime(dto.dt, zoneId),
            temperature = dto.temp,
            feelsLike = dto.feelsLike,
            humidity = dto.humidity,
            dewPoint = dto.dewPoint,
            precipitationProbability = ((dto.pop ?: 0.0) * 100).toInt(),
            weatherCondition = WeatherCondition.fromOpenWeatherMapId(
                dto.weather.firstOrNull()?.id ?: 800,
                isDay = isDay
            ),
            isDay = isDay,
            pressure = dto.pressure.toDouble(),
            windSpeed = dto.windSpeed,
            windDirection = WindDirection.fromDegrees(dto.windDeg),
            windDirectionDegrees = dto.windDeg,
            visibility = (dto.visibility ?: 10000).toDouble(),
            uvIndex = dto.uvi
        )
    }

    private fun mapDaily(
        dto: OpenWeatherMapDailyDto,
        zoneId: ZoneId
    ): DailyForecast {
        val sunrise = dto.sunrise?.let { toLocalTime(it, zoneId) } ?: LocalTime.of(6, 0)
        val sunset = dto.sunset?.let { toLocalTime(it, zoneId) } ?: LocalTime.of(18, 0)
        val daylightDurationSeconds = if (sunset.isAfter(sunrise)) {
            (sunset.toSecondOfDay() - sunrise.toSecondOfDay()).toDouble()
        } else {
            43200.0
        }

        val date = Instant.ofEpochSecond(dto.dt).atZone(zoneId).toLocalDate()

        return DailyForecast(
            date = date,
            weatherCondition = WeatherCondition.fromOpenWeatherMapId(
                dto.weather.firstOrNull()?.id ?: 800,
                isDay = true
            ),
            temperatureMax = dto.temp.max,
            temperatureMin = dto.temp.min,
            feelsLikeMax = maxOf(dto.feelsLike.day, dto.feelsLike.eve),
            feelsLikeMin = minOf(dto.feelsLike.morn, dto.feelsLike.night),
            sunrise = sunrise,
            sunset = sunset,
            daylightDurationSeconds = daylightDurationSeconds,
            precipitationSum = (dto.rain ?: 0.0) + (dto.snow ?: 0.0),
            precipitationProbability = ((dto.pop ?: 0.0) * 100).toInt(),
            windSpeedMax = dto.windSpeed,
            windDirectionDominant = WindDirection.fromDegrees(dto.windDeg),
            windDirectionDegrees = dto.windDeg,
            uvIndexMax = dto.uvi,
            averageHumidity = dto.humidity,
            averagePressure = dto.pressure.toDouble()
        )
    }

    private fun zoneId(response: OpenWeatherMapOneCallResponseDto): ZoneId =
        response.timezone
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneOffset.ofTotalSeconds(response.timezoneOffset ?: 0)

    private fun toLocalDateTime(epochSeconds: Long, zoneId: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zoneId)

    private fun toLocalTime(epochSeconds: Long, zoneId: ZoneId): LocalTime =
        Instant.ofEpochSecond(epochSeconds).atZone(zoneId).toLocalTime()

    private fun currentIsDay(
        timestamp: Long,
        sunrise: Long?,
        sunset: Long?,
        icon: String?
    ): Boolean {
        if (sunrise != null && sunset != null) {
            return timestamp in sunrise..<sunset
        }
        return iconIndicatesDay(icon)
    }

    private fun iconIndicatesDay(icon: String?): Boolean = icon?.endsWith("d") != false
}
