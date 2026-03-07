package com.clockweather.app.data.mapper

import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.local.entity.DailyForecastEntity
import com.clockweather.app.data.local.entity.HourlyForecastEntity
import com.clockweather.app.data.local.entity.LocationEntity
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WindDirection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class WeatherEntityMapper @Inject constructor() {

    // ── Location ──────────────────────────────────────────────────────────────

    fun mapLocationToDomain(entity: LocationEntity): Location = Location(
        id = entity.id,
        name = entity.name,
        country = entity.country,
        latitude = entity.latitude,
        longitude = entity.longitude,
        timezone = entity.timezone,
        isCurrentLocation = entity.isCurrentLocation
    )

    fun mapLocationToEntity(domain: Location): LocationEntity = LocationEntity(
        id = domain.id,
        name = domain.name,
        country = domain.country,
        latitude = domain.latitude,
        longitude = domain.longitude,
        timezone = domain.timezone,
        isCurrentLocation = domain.isCurrentLocation
    )

    // ── CurrentWeather ────────────────────────────────────────────────────────

    fun mapCurrentWeatherToDomain(entity: CurrentWeatherEntity): CurrentWeather = CurrentWeather(
        temperature = entity.temperature,
        feelsLikeTemperature = entity.feelsLikeTemperature,
        humidity = entity.humidity,
        dewPoint = entity.dewPoint,
        precipitation = entity.precipitation,
        precipitationProbability = entity.precipitationProbability,
        weatherCondition = WeatherCondition.fromCode(entity.weatherCode, entity.isDay),
        isDay = entity.isDay,
        pressure = entity.pressure,
        windSpeed = entity.windSpeed,
        windDirection = WindDirection.fromDegrees(entity.windDirectionDegrees),
        windDirectionDegrees = entity.windDirectionDegrees,
        windGusts = entity.windGusts,
        visibility = entity.visibility,
        uvIndex = entity.uvIndex,
        cloudCover = entity.cloudCover,
        lastUpdated = LocalDateTime.parse(entity.lastUpdated, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )

    fun mapAirQualityFromEntity(entity: CurrentWeatherEntity): AirQuality? {
        val epa = entity.aqUsEpaIndex ?: return null
        return AirQuality(
            co = entity.aqCo ?: 0.0,
            no2 = entity.aqNo2 ?: 0.0,
            o3 = entity.aqO3 ?: 0.0,
            so2 = entity.aqSo2 ?: 0.0,
            pm25 = entity.aqPm25 ?: 0.0,
            pm10 = entity.aqPm10 ?: 0.0,
            usEpaIndex = epa,
            gbDefraIndex = entity.aqGbDefraIndex ?: 1
        )
    }

    fun mapCurrentWeatherToEntity(domain: CurrentWeather, locationId: Long, airQuality: AirQuality? = null): CurrentWeatherEntity =
        CurrentWeatherEntity(
            locationId = locationId,
            temperature = domain.temperature,
            feelsLikeTemperature = domain.feelsLikeTemperature,
            humidity = domain.humidity,
            dewPoint = domain.dewPoint,
            precipitation = domain.precipitation,
            precipitationProbability = domain.precipitationProbability,
            weatherCode = domain.weatherCondition.toCode(),
            isDay = domain.isDay,
            pressure = domain.pressure,
            windSpeed = domain.windSpeed,
            windDirectionDegrees = domain.windDirectionDegrees,
            windGusts = domain.windGusts,
            visibility = domain.visibility,
            uvIndex = domain.uvIndex,
            cloudCover = domain.cloudCover,
            lastUpdated = domain.lastUpdated.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            aqCo = airQuality?.co,
            aqNo2 = airQuality?.no2,
            aqO3 = airQuality?.o3,
            aqSo2 = airQuality?.so2,
            aqPm25 = airQuality?.pm25,
            aqPm10 = airQuality?.pm10,
            aqUsEpaIndex = airQuality?.usEpaIndex,
            aqGbDefraIndex = airQuality?.gbDefraIndex
        )

    // ── HourlyForecast ────────────────────────────────────────────────────────

    fun mapHourlyToDomain(entity: HourlyForecastEntity): HourlyForecast = HourlyForecast(
        dateTime = LocalDateTime.parse(entity.dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        temperature = entity.temperature,
        feelsLike = entity.feelsLike,
        humidity = entity.humidity,
        dewPoint = entity.dewPoint,
        precipitationProbability = entity.precipitationProbability,
        weatherCondition = WeatherCondition.fromCode(entity.weatherCode, entity.isDay),
        isDay = entity.isDay,
        pressure = entity.pressure,
        windSpeed = entity.windSpeed,
        windDirection = WindDirection.fromDegrees(entity.windDirectionDegrees),
        windDirectionDegrees = entity.windDirectionDegrees,
        visibility = entity.visibility,
        uvIndex = entity.uvIndex
    )

    fun mapHourlyToEntity(domain: HourlyForecast, locationId: Long): HourlyForecastEntity =
        HourlyForecastEntity(
            locationId = locationId,
            dateTime = domain.dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            temperature = domain.temperature,
            feelsLike = domain.feelsLike,
            humidity = domain.humidity,
            dewPoint = domain.dewPoint,
            precipitationProbability = domain.precipitationProbability,
            weatherCode = domain.weatherCondition.toCode(),
            isDay = domain.isDay,
            pressure = domain.pressure,
            windSpeed = domain.windSpeed,
            windDirectionDegrees = domain.windDirectionDegrees,
            visibility = domain.visibility,
            uvIndex = domain.uvIndex
        )

    // ── DailyForecast ─────────────────────────────────────────────────────────

    fun mapDailyToDomain(entity: DailyForecastEntity): DailyForecast = DailyForecast(
        date = LocalDate.parse(entity.date, DateTimeFormatter.ISO_LOCAL_DATE),
        weatherCondition = WeatherCondition.fromCode(entity.weatherCode, isDay = true),
        temperatureMax = entity.temperatureMax,
        temperatureMin = entity.temperatureMin,
        feelsLikeMax = entity.feelsLikeMax,
        feelsLikeMin = entity.feelsLikeMin,
        sunrise = LocalTime.parse(entity.sunrise, DateTimeFormatter.ofPattern("HH:mm")),
        sunset = LocalTime.parse(entity.sunset, DateTimeFormatter.ofPattern("HH:mm")),
        daylightDurationSeconds = entity.daylightDurationSeconds,
        precipitationSum = entity.precipitationSum,
        precipitationProbability = entity.precipitationProbability,
        windSpeedMax = entity.windSpeedMax,
        windDirectionDominant = WindDirection.fromDegrees(entity.windDirectionDegrees),
        windDirectionDegrees = entity.windDirectionDegrees,
        uvIndexMax = entity.uvIndexMax,
        averageHumidity = entity.averageHumidity,
        averagePressure = entity.averagePressure
    )

    fun mapDailyToEntity(domain: DailyForecast, locationId: Long): DailyForecastEntity =
        DailyForecastEntity(
            locationId = locationId,
            date = domain.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            weatherCode = domain.weatherCondition.toCode(),
            temperatureMax = domain.temperatureMax,
            temperatureMin = domain.temperatureMin,
            feelsLikeMax = domain.feelsLikeMax,
            feelsLikeMin = domain.feelsLikeMin,
            sunrise = domain.sunrise.format(DateTimeFormatter.ofPattern("HH:mm")),
            sunset = domain.sunset.format(DateTimeFormatter.ofPattern("HH:mm")),
            daylightDurationSeconds = domain.daylightDurationSeconds,
            precipitationSum = domain.precipitationSum,
            precipitationProbability = domain.precipitationProbability,
            windSpeedMax = domain.windSpeedMax,
            windDirectionDegrees = domain.windDirectionDegrees,
            uvIndexMax = domain.uvIndexMax,
            averageHumidity = domain.averageHumidity,
            averagePressure = domain.averagePressure
        )
}

