package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastDto
import com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GooglePollenDayInfoDto
import com.clockweather.app.data.remote.dto.google.GooglePollenForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GooglePollenPlantInfoDto
import com.clockweather.app.data.remote.dto.google.GooglePollenTypeInfoDto
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.PlantPollen
import com.clockweather.app.domain.model.PollenData
import com.clockweather.app.domain.model.PollenType
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
        pollen: GooglePollenForecastResponseDto? = null,
        airQuality: com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto? = null,
        location: Location
    ): WeatherData {
        val timezone = current.timeZone?.id ?: "UTC"
        val pollenByDate = mapPollenByDate(pollen)

        return WeatherData(
            location = location,
            currentWeather = mapCurrent(current),
            hourlyForecasts = hourly?.forecastHours?.map { mapHourly(it) } ?: emptyList(),
            dailyForecasts = daily.forecastDays.map { dto ->
                val date = runCatching { LocalDate.of(dto.displayDate.year, dto.displayDate.month, dto.displayDate.day) }
                    .getOrElse { LocalDate.now() }
                mapDaily(dto, timezone, pollen = pollenByDate[date])
            },
            airQuality = mapAirQuality(airQuality)
        )
    }

    internal fun mapAirQuality(dto: com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto?): AirQuality? {
        if (dto == null) return null
        val indexes = dto.indexes.orEmpty()
        val pollutants = dto.pollutants.orEmpty()

        if (indexes.isEmpty() && pollutants.isEmpty()) return null

        fun pollutantValue(code: String): Double {
            val pollutant = pollutants.firstOrNull { it.code.equals(code, ignoreCase = true) }
            val raw = pollutant?.concentration?.value ?: return 0.0
            val units = pollutant.concentration.units.orEmpty()
            return when {
                units.contains("PARTS_PER_BILLION", ignoreCase = true) -> {
                    when (code.lowercase()) {
                        "no2" -> raw * 1.88
                        "o3" -> raw * 2.0
                        "so2" -> raw * 2.62
                        "co" -> raw * 1.15
                        else -> raw
                    }
                }
                units.contains("PARTS_PER_MILLION", ignoreCase = true) -> {
                    when (code.lowercase()) {
                        "co" -> raw * 1150.0
                        else -> raw * 1000.0
                    }
                }
                else -> raw
            }
        }

        val pm25 = pollutantValue("pm25")
        val pm10 = pollutantValue("pm10")
        val co = pollutantValue("co")
        val no2 = pollutantValue("no2")
        val so2 = pollutantValue("so2")
        val o3 = pollutantValue("o3")

        val usaEpaAqi = indexes.firstOrNull { it.code.equals("usa_epa", ignoreCase = true) }?.aqi
        val uaqiAqi = indexes.firstOrNull { it.code.equals("uaqi", ignoreCase = true) }?.aqi

        val usEpaIndex = when {
            usaEpaAqi != null -> when {
                usaEpaAqi <= 50 -> 1
                usaEpaAqi <= 100 -> 2
                usaEpaAqi <= 150 -> 3
                usaEpaAqi <= 200 -> 4
                usaEpaAqi <= 300 -> 5
                else -> 6
            }
            uaqiAqi != null -> when {
                uaqiAqi >= 80 -> 1
                uaqiAqi >= 60 -> 2
                uaqiAqi >= 40 -> 3
                uaqiAqi >= 20 -> 4
                uaqiAqi >= 10 -> 5
                else -> 6
            }
            pm25 > 0.0 || pm10 > 0.0 -> when {
                pm25 <= 12.0 && pm10 <= 54.0 -> 1
                pm25 <= 35.4 && pm10 <= 154.0 -> 2
                pm25 <= 55.4 && pm10 <= 254.0 -> 3
                pm25 <= 150.4 && pm10 <= 354.0 -> 4
                pm25 <= 250.4 && pm10 <= 424.0 -> 5
                else -> 6
            }
            else -> 1
        }

        val gbDefraAqi = indexes.firstOrNull {
            it.code.equals("gb_daqi", ignoreCase = true) ||
            it.code.equals("gb_defra", ignoreCase = true) ||
            it.code.equals("gbr_defra", ignoreCase = true)
        }?.aqi ?: when (usEpaIndex) {
            1 -> 1
            2 -> 4
            3 -> 6
            4 -> 7
            5 -> 9
            else -> 10
        }

        return AirQuality(
            co = co,
            no2 = no2,
            o3 = o3,
            so2 = so2,
            pm25 = pm25,
            pm10 = pm10,
            usEpaIndex = usEpaIndex,
            gbDefraIndex = gbDefraAqi.coerceIn(1, 10)
        )
    }

    private fun mapCurrent(dto: GoogleCurrentConditionsDto): CurrentWeather {
        val windDeg = dto.wind?.direction?.degrees?.toInt() ?: 0
        // Stamp fetch time so the 10-min TTL is relative to when we fetched, not the API observation.
        val lastUpdated = LocalDateTime.now()

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

    private fun mapDaily(dto: GoogleDailyForecastDto, timezone: String, pollen: PollenData? = null): DailyForecast {
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
            averagePressure = 1013.25, // Google daily forecast does not include pressure
            pollen = pollen
        )
    }

    private fun mapPollenByDate(pollenDto: GooglePollenForecastResponseDto?): Map<LocalDate, PollenData> {
        if (pollenDto == null || pollenDto.dailyInfo.isEmpty()) return emptyMap()

        return pollenDto.dailyInfo.mapNotNull { dayInfo ->
            val dateDto = dayInfo.date
            val date = runCatching { LocalDate.of(dateDto.year, dateDto.month, dateDto.day) }.getOrNull()
                ?: return@mapNotNull null
            val pollenData = mapDailyPollen(dayInfo) ?: return@mapNotNull null
            date to pollenData
        }.toMap()
    }

    private fun mapDailyPollen(dayInfo: GooglePollenDayInfoDto): PollenData? {
        val types = dayInfo.pollenTypeInfo
        val plants = dayInfo.plantInfo

        if (types.isEmpty() && plants.isEmpty()) return null

        var grass: PollenType? = null
        var tree: PollenType? = null
        var weed: PollenType? = null
        val recommendations = mutableListOf<String>()

        for (t in types) {
            val mapped = mapPollenType(t)
            val upperCode = t.code.uppercase()
            when {
                upperCode.contains("GRASS") -> grass = mapped
                upperCode.contains("TREE") -> tree = mapped
                upperCode.contains("WEED") -> weed = mapped
            }
            mapped.healthRecommendations.forEach { rec ->
                if (rec.isNotBlank() && rec !in recommendations) {
                    recommendations.add(rec)
                }
            }
        }

        val dominantPlants = plants.map { mapPlantPollen(it) }

        val pollenData = PollenData(
            grassPollen = grass,
            treePollen = tree,
            weedPollen = weed,
            dominantPlants = dominantPlants,
            healthRecommendations = recommendations
        )

        return if (pollenData.hasData) pollenData else null
    }

    private fun mapPollenType(dto: GooglePollenTypeInfoDto): PollenType = PollenType(
        code = dto.code,
        displayName = dto.displayName.ifBlank { dto.code.lowercase().replaceFirstChar { it.titlecase() } },
        inSeason = dto.inSeason ?: false,
        indexValue = dto.indexInfo?.value,
        category = dto.indexInfo?.category,
        healthRecommendations = dto.healthRecommendations.orEmpty()
    )

    private fun mapPlantPollen(dto: GooglePollenPlantInfoDto): PlantPollen = PlantPollen(
        code = dto.code,
        displayName = dto.displayName.ifBlank { dto.code.lowercase().replaceFirstChar { it.titlecase() } },
        inSeason = dto.inSeason ?: false,
        indexValue = dto.indexInfo?.value,
        category = dto.indexInfo?.category
    )

    private fun parseUtcToLocalTime(utcTimestamp: String?, zone: ZoneId): LocalTime? {
        utcTimestamp ?: return null
        return runCatching {
            Instant.parse(utcTimestamp).atZone(zone).toLocalTime()
        }.getOrNull()
    }
}

