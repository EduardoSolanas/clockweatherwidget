package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.CurrentWeatherDto
import com.clockweather.app.data.remote.dto.DailyWeatherDto
import com.clockweather.app.data.remote.dto.GeoLocationDto
import com.clockweather.app.data.remote.dto.HourlyWeatherDto
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class WeatherDtoMapper @Inject constructor() {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun mapToWeatherData(
        response: WeatherResponseDto,
        location: Location
    ): WeatherData {
        val currentWeather = mapCurrentWeather(requireNotNull(response.current) { "current weather is null" })
        val hourlyForecasts = response.hourly?.let { mapHourlyForecasts(it) } ?: emptyList()
        val dailyForecasts = response.daily?.let { mapDailyForecasts(it, hourlyForecasts) } ?: emptyList()

        return WeatherData(
            location = location,
            currentWeather = currentWeather,
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = dailyForecasts
        )
    }

    private fun mapCurrentWeather(dto: CurrentWeatherDto): CurrentWeather {
        val isDay = dto.isDay == 1
        return CurrentWeather(
            temperature = dto.temperature,
            feelsLikeTemperature = dto.apparentTemperature,
            humidity = dto.relativeHumidity,
            dewPoint = 0.0, // not in current endpoint, computed if needed
            precipitation = dto.precipitation,
            precipitationProbability = 0, // not in current endpoint
            weatherCondition = WeatherCondition.fromCode(dto.weatherCode, isDay),
            isDay = isDay,
            pressure = dto.pressureMsl,
            windSpeed = dto.windSpeed,
            windDirection = WindDirection.fromDegrees(dto.windDirection),
            windDirectionDegrees = dto.windDirection,
            windGusts = dto.windGusts,
            visibility = 0.0, // not in current endpoint
            uvIndex = 0.0, // not in current endpoint
            cloudCover = dto.cloudCover,
            lastUpdated = LocalDateTime.parse(dto.time, dateTimeFormatter)
        )
    }

    private fun mapHourlyForecasts(dto: HourlyWeatherDto): List<HourlyForecast> {
        return dto.time.indices.map { i ->
            val isDay = dto.isDay[i] == 1
            HourlyForecast(
                dateTime = LocalDateTime.parse(dto.time[i], dateTimeFormatter),
                temperature = dto.temperature[i],
                feelsLike = dto.apparentTemperature[i],
                humidity = dto.relativeHumidity[i],
                dewPoint = dto.dewPoint[i],
                precipitationProbability = dto.precipitationProbability.getOrElse(i) { 0 },
                weatherCondition = WeatherCondition.fromCode(dto.weatherCode[i], isDay),
                isDay = isDay,
                pressure = dto.pressureMsl[i],
                windSpeed = dto.windSpeed[i],
                windDirection = WindDirection.fromDegrees(dto.windDirection[i]),
                windDirectionDegrees = dto.windDirection[i],
                visibility = dto.visibility[i],
                uvIndex = dto.uvIndex.getOrElse(i) { 0.0 }
            )
        }
    }

    private fun mapDailyForecasts(
        dto: DailyWeatherDto,
        hourlyForecasts: List<HourlyForecast>
    ): List<DailyForecast> {
        return dto.time.indices.map { i ->
            val date = LocalDate.parse(dto.time[i], dateFormatter)

            // Aggregate hourly data for this day
            val dayHourly = hourlyForecasts.filter { it.dateTime.toLocalDate() == date }
            val avgHumidity = if (dayHourly.isNotEmpty()) dayHourly.map { it.humidity }.average().toInt() else 0
            val avgPressure = if (dayHourly.isNotEmpty()) dayHourly.map { it.pressure }.average() else 0.0

            val sunriseStr = dto.sunrise[i]
            val sunsetStr = dto.sunset[i]
            val sunrise = runCatching { LocalDateTime.parse(sunriseStr, timeFormatter).toLocalTime() }
                .getOrElse { LocalTime.of(6, 0) }
            val sunset = runCatching { LocalDateTime.parse(sunsetStr, timeFormatter).toLocalTime() }
                .getOrElse { LocalTime.of(18, 0) }

            DailyForecast(
                date = date,
                weatherCondition = WeatherCondition.fromCode(dto.weatherCode[i], isDay = true),
                temperatureMax = dto.temperatureMax[i],
                temperatureMin = dto.temperatureMin[i],
                feelsLikeMax = dto.apparentTemperatureMax[i],
                feelsLikeMin = dto.apparentTemperatureMin[i],
                sunrise = sunrise,
                sunset = sunset,
                daylightDurationSeconds = dto.daylightDuration[i],
                precipitationSum = dto.precipitationSum[i],
                precipitationProbability = dto.precipitationProbabilityMax[i],
                windSpeedMax = dto.windSpeedMax[i],
                windDirectionDominant = WindDirection.fromDegrees(dto.windDirectionDominant[i]),
                windDirectionDegrees = dto.windDirectionDominant[i],
                uvIndexMax = dto.uvIndexMax[i],
                averageHumidity = avgHumidity,
                averagePressure = avgPressure
            )
        }
    }

    fun mapGeoLocation(dto: GeoLocationDto): Location {
        val admin4 = dto.admin4?.trim()?.takeIf { it.isNotBlank() }
        val admin3 = dto.admin3?.trim()?.takeIf { it.isNotBlank() }
        val admin2 = dto.admin2?.trim()?.takeIf { it.isNotBlank() }
        val admin1 = dto.admin1?.trim()?.takeIf { it.isNotBlank() }
        val explicitName = dto.name.trim().takeIf { it.isNotBlank() }
            ?.takeUnless { it == admin4 || it == admin3 || it == admin2 || it == admin1 }

        val resolvedName = admin4
            ?: admin3
            ?: explicitName
            ?: admin2
            ?: admin1
            ?: dto.name

        return Location(
            id = dto.id,
            name = resolvedName,
            country = dto.country ?: dto.countryCode ?: "",
            latitude = dto.latitude,
            longitude = dto.longitude,
            timezone = dto.timezone ?: "auto",
            isCurrentLocation = false
        )
    }
}

