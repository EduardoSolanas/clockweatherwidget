package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiAirQualityDto
import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiCurrentDto
import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiDayDto
import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiForecastDayDto
import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiHourDto
import com.clockweather.app.data.remote.dto.weatherapi.WeatherApiResponseDto
import com.clockweather.app.domain.model.AirQuality
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

class WeatherApiMapper @Inject constructor() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    // WeatherAPI astro times: "06:34 AM"
    private val astroTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    fun mapToWeatherData(response: WeatherApiResponseDto, location: Location): WeatherData {
        val current = mapCurrent(response.current)
        val dailyForecasts = response.forecast.forecastDay.map { mapForecastDay(it) }
        val hourlyForecasts = response.forecast.forecastDay.flatMap { day ->
            day.hour.map { mapHour(it) }
        }
        val airQuality = response.current.airQuality?.let { mapAirQuality(it) }
        return WeatherData(
            location = location,
            currentWeather = current,
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = dailyForecasts,
            airQuality = airQuality
        )
    }

    private fun mapAirQuality(dto: WeatherApiAirQualityDto): AirQuality = AirQuality(
        co = dto.co ?: 0.0,
        no2 = dto.no2 ?: 0.0,
        o3 = dto.o3 ?: 0.0,
        so2 = dto.so2 ?: 0.0,
        pm25 = dto.pm25 ?: 0.0,
        pm10 = dto.pm10 ?: 0.0,
        usEpaIndex = dto.usEpaIndex ?: 1,
        gbDefraIndex = dto.gbDefraIndex ?: 1
    )

    private fun mapCurrent(dto: WeatherApiCurrentDto): CurrentWeather {
        val isDay = dto.isDay == 1
        return CurrentWeather(
            temperature = dto.tempC,
            feelsLikeTemperature = dto.feelslikeC,
            humidity = dto.humidity,
            dewPoint = dto.dewpointC,
            precipitation = dto.precipMm,
            precipitationProbability = 0,
            weatherCondition = WeatherCondition.fromWeatherApiCode(dto.condition.code, isDay),
            isDay = isDay,
            pressure = dto.pressureMb,
            windSpeed = dto.windKph,
            windDirection = WindDirection.fromLabel(dto.windDir),
            windDirectionDegrees = dto.windDegree,
            windGusts = dto.gustKph,
            visibility = dto.visKm * 1000.0, // convert km → m to match domain
            uvIndex = dto.uv,
            cloudCover = dto.cloud,
            lastUpdated = runCatching {
                LocalDateTime.parse(dto.lastUpdated, dateTimeFormatter)
            }.getOrElse { LocalDateTime.now() }
        )
    }

    private fun mapForecastDay(dto: WeatherApiForecastDayDto): DailyForecast {
        val date = LocalDate.parse(dto.date, dateFormatter)
        val day = dto.day
        val astro = dto.astro

        val sunrise = runCatching {
            LocalTime.parse(astro.sunrise.trim().uppercase(), astroTimeFormatter)
        }.getOrElse { LocalTime.of(6, 0) }

        val sunset = runCatching {
            LocalTime.parse(astro.sunset.trim().uppercase(), astroTimeFormatter)
        }.getOrElse { LocalTime.of(18, 0) }

        val daylightSeconds = if (sunset.isAfter(sunrise)) {
            (sunset.toSecondOfDay() - sunrise.toSecondOfDay()).toDouble()
        } else 43200.0

        // Dominant wind direction from hourly majority
        val dominantWindDir = dto.hour
            .groupBy { it.windDir }
            .maxByOrNull { it.value.size }
            ?.key ?: "N"
        val dominantWindDeg = dto.hour.map { it.windDegree }.average().toInt()

        val avgPressure = dto.hour.map { it.pressureMb }.average()

        return DailyForecast(
            date = date,
            weatherCondition = WeatherCondition.fromWeatherApiCode(day.condition.code, isDay = true),
            temperatureMax = day.maxtempC,
            temperatureMin = day.mintempC,
            feelsLikeMax = dto.hour.maxOfOrNull { it.feelslikeC } ?: day.maxtempC,
            feelsLikeMin = dto.hour.minOfOrNull { it.feelslikeC } ?: day.mintempC,
            sunrise = sunrise,
            sunset = sunset,
            daylightDurationSeconds = daylightSeconds,
            precipitationSum = day.totalprecipMm,
            precipitationProbability = day.dailyChanceOfRain,
            windSpeedMax = day.maxwindKph,
            windDirectionDominant = WindDirection.fromLabel(dominantWindDir),
            windDirectionDegrees = dominantWindDeg,
            uvIndexMax = day.uv,
            averageHumidity = day.avghumidity.toInt(),
            averagePressure = avgPressure
        )
    }

    private fun mapHour(dto: WeatherApiHourDto): HourlyForecast {
        val isDay = dto.isDay == 1
        return HourlyForecast(
            dateTime = runCatching {
                LocalDateTime.parse(dto.time, dateTimeFormatter)
            }.getOrElse { LocalDateTime.now() },
            temperature = dto.tempC,
            feelsLike = dto.feelslikeC,
            humidity = dto.humidity,
            dewPoint = dto.dewpointC,
            precipitationProbability = dto.chanceOfRain,
            weatherCondition = WeatherCondition.fromWeatherApiCode(dto.condition.code, isDay),
            isDay = isDay,
            pressure = dto.pressureMb,
            windSpeed = dto.windKph,
            windDirection = WindDirection.fromLabel(dto.windDir),
            windDirectionDegrees = dto.windDegree,
            visibility = dto.visKm * 1000.0,
            uvIndex = dto.uv
        )
    }
}

