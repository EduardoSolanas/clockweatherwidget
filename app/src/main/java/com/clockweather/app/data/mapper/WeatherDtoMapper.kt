package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.CurrentWeatherDto
import com.clockweather.app.data.remote.dto.DailyWeatherDto
import com.clockweather.app.data.remote.dto.GeoLocationDto
import com.clockweather.app.data.remote.dto.HourlyWeatherDto
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityHourlyDto
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityResponseDto
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
        location: Location,
        airQualityResponse: OpenMeteoAirQualityResponseDto? = null
    ): WeatherData {
        // Stamp fetch time so the 10-min TTL is relative to when we fetched, not the API model slot
        // (Open-Meteo's current.time can be 15+ min behind actual fetch time).
        val fetchTime = LocalDateTime.now()
        val currentWeather = mapCurrentWeather(requireNotNull(response.current) { "current weather is null" }, fetchTime)
        val hourlyForecasts = response.hourly?.let { mapHourlyForecasts(it) } ?: emptyList()
        val dailyForecasts = response.daily?.let { mapDailyForecasts(it, hourlyForecasts, airQualityResponse) } ?: emptyList()
        val airQuality = mapAirQuality(airQualityResponse)

        return WeatherData(
            location = location,
            currentWeather = currentWeather,
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = dailyForecasts,
            airQuality = airQuality
        )
    }

    private fun mapCurrentWeather(dto: CurrentWeatherDto, fetchTime: LocalDateTime): CurrentWeather {
        val isDay = dto.isDay == 1
        return CurrentWeather(
            temperature = dto.temperature,
            feelsLikeTemperature = dto.apparentTemperature,
            humidity = dto.relativeHumidity,
            dewPoint = dto.dewPoint,
            precipitation = dto.precipitation,
            precipitationProbability = 0, // not in current endpoint
            weatherCondition = WeatherCondition.fromCode(dto.weatherCode, isDay),
            isDay = isDay,
            pressure = dto.pressureMsl,
            windSpeed = dto.windSpeed,
            windDirection = WindDirection.fromDegrees(dto.windDirection),
            windDirectionDegrees = dto.windDirection,
            windGusts = dto.windGusts,
            visibility = dto.visibility,
            uvIndex = dto.uvIndex,
            cloudCover = dto.cloudCover,
            lastUpdated = fetchTime
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
        hourlyForecasts: List<HourlyForecast>,
        airQualityResponse: OpenMeteoAirQualityResponseDto? = null
    ): List<DailyForecast> {
        val hourlyAirQuality = airQualityResponse?.hourly
        return dto.time.indices.map { i ->
            val date = LocalDate.parse(dto.time[i], dateFormatter)

            val dayHourly = hourlyForecasts.filter { it.dateTime.toLocalDate() == date }
            val avgHumidity = if (dayHourly.isNotEmpty()) dayHourly.map { it.humidity }.average().toInt() else 0
            val avgPressure = if (dayHourly.isNotEmpty()) dayHourly.map { it.pressure }.average() else 0.0

            val sunriseStr = dto.sunrise[i]
            val sunsetStr = dto.sunset[i]
            val sunrise = runCatching { LocalDateTime.parse(sunriseStr, timeFormatter).toLocalTime() }
                .getOrElse { LocalTime.of(6, 0) }
            val sunset = runCatching { LocalDateTime.parse(sunsetStr, timeFormatter).toLocalTime() }
                .getOrElse { LocalTime.of(18, 0) }

            val pollen = mapOpenMeteoPollenForDate(date, hourlyAirQuality)

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
                averagePressure = avgPressure,
                pollen = pollen
            )
        }
    }

    private fun mapAirQuality(dto: OpenMeteoAirQualityResponseDto?): AirQuality? {
        val hourly = dto?.hourly ?: return null
        if (hourly.time.isEmpty()) return null

        val pm25 = hourly.pm25?.filterNotNull()?.maxOrNull() ?: 0.0
        val pm10 = hourly.pm10?.filterNotNull()?.maxOrNull() ?: 0.0
        val co = hourly.carbonMonoxide?.filterNotNull()?.maxOrNull() ?: 0.0
        val no2 = hourly.nitrogenDioxide?.filterNotNull()?.maxOrNull() ?: 0.0
        val so2 = hourly.sulphurDioxide?.filterNotNull()?.maxOrNull() ?: 0.0
        val o3 = hourly.ozone?.filterNotNull()?.maxOrNull() ?: 0.0
        val rawUsAqi = hourly.usAqi?.filterNotNull()?.maxOrNull() ?: 0
        val rawDefra = hourly.europeanAqi?.filterNotNull()?.maxOrNull() ?: 1

        if (pm25 == 0.0 && pm10 == 0.0 && co == 0.0 && no2 == 0.0 && so2 == 0.0 && o3 == 0.0 && rawUsAqi == 0) {
            return null
        }

        val usEpaIndex = when {
            rawUsAqi <= 50 -> 1   // Good
            rawUsAqi <= 100 -> 2  // Moderate
            rawUsAqi <= 150 -> 3  // Unhealthy for Sensitive
            rawUsAqi <= 200 -> 4  // Unhealthy
            rawUsAqi <= 300 -> 5  // Very Unhealthy
            else -> 6             // Hazardous
        }

        return AirQuality(
            co = co,
            no2 = no2,
            o3 = o3,
            so2 = so2,
            pm25 = pm25,
            pm10 = pm10,
            usEpaIndex = usEpaIndex,
            gbDefraIndex = rawDefra.coerceIn(1, 10)
        )
    }

    private fun mapOpenMeteoPollenForDate(
        date: LocalDate,
        hourly: OpenMeteoAirQualityHourlyDto?
    ): PollenData? {
        if (hourly == null || hourly.time.isEmpty()) return null

        // Find hourly indices that correspond to this date
        val dateIndices = hourly.time.indices.filter { idx ->
            runCatching { LocalDate.parse(hourly.time[idx].substringBefore("T")) }.getOrNull() == date
        }
        if (dateIndices.isEmpty()) return null

        fun peak(values: List<Double?>?): Double {
            if (values == null) return 0.0
            return dateIndices.mapNotNull { values.getOrNull(it) }.maxOrNull() ?: 0.0
        }

        val grassPeak = peak(hourly.grassPollen)
        val birchPeak = peak(hourly.birchPollen)
        val alderPeak = peak(hourly.alderPollen)
        val olivePeak = peak(hourly.olivePollen)
        val treePeak = maxOf(birchPeak, alderPeak, olivePeak)
        val ragweedPeak = peak(hourly.ragweedPollen)
        val mugwortPeak = peak(hourly.mugwortPollen)
        val weedPeak = maxOf(ragweedPeak, mugwortPeak)

        val totalPollen = grassPeak + treePeak + weedPeak
        if (totalPollen <= 0.0) return null

        val grassType = mapPollenTypeFromConcentration("GRASS", "Grass", grassPeak, isTree = false)
        val treeType = mapPollenTypeFromConcentration("TREE", "Tree", treePeak, isTree = true)
        val weedType = mapPollenTypeFromConcentration("WEED", "Weed", weedPeak, isTree = false)

        val dominantPlants = mutableListOf<PlantPollen>()
        if (birchPeak > 0) dominantPlants.add(PlantPollen("BIRCH", "Birch", inSeason = true))
        if (alderPeak > 0) dominantPlants.add(PlantPollen("ALDER", "Alder", inSeason = true))
        if (olivePeak > 0) dominantPlants.add(PlantPollen("OLIVE", "Olive", inSeason = true))
        if (grassPeak > 0) dominantPlants.add(PlantPollen("GRASS", "Grass", inSeason = true))
        if (ragweedPeak > 0) dominantPlants.add(PlantPollen("RAGWEED", "Ragweed", inSeason = true))
        if (mugwortPeak > 0) dominantPlants.add(PlantPollen("MUGWORT", "Mugwort", inSeason = true))

        val pollenData = PollenData(
            grassPollen = grassType,
            treePollen = treeType,
            weedPollen = weedType,
            dominantPlants = dominantPlants,
            healthRecommendations = emptyList()
        )

        return if (pollenData.hasData) pollenData else null
    }

    private fun mapPollenTypeFromConcentration(
        code: String,
        displayName: String,
        peakValue: Double,
        isTree: Boolean
    ): PollenType? {
        if (peakValue <= 0.0) return null

        val (indexValue, category) = if (isTree) {
            when {
                peakValue <= 10.0 -> 2 to "Low"
                peakValue <= 100.0 -> 3 to "Moderate"
                peakValue <= 1000.0 -> 4 to "High"
                else -> 5 to "Very High"
            }
        } else {
            when {
                peakValue <= 10.0 -> 2 to "Low"
                peakValue <= 50.0 -> 3 to "Moderate"
                peakValue <= 200.0 -> 4 to "High"
                else -> 5 to "Very High"
            }
        }

        return PollenType(
            code = code,
            displayName = displayName,
            inSeason = true,
            indexValue = indexValue,
            category = category
        )
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

