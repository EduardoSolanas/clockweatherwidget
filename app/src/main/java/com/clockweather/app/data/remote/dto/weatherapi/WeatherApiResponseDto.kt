package com.clockweather.app.data.remote.dto.weatherapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Root ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WeatherApiResponseDto(
    val location: WeatherApiLocationDto,
    val current: WeatherApiCurrentDto,
    val forecast: WeatherApiForecastDto
)

// ─── Location ─────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WeatherApiLocationDto(
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    @Json(name = "tz_id") val tzId: String,
    @Json(name = "localtime_epoch") val localtimeEpoch: Long,
    val localtime: String
)

// ─── Current ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WeatherApiCurrentDto(
    @Json(name = "last_updated") val lastUpdated: String,
    @Json(name = "temp_c") val tempC: Double,
    @Json(name = "temp_f") val tempF: Double,
    @Json(name = "is_day") val isDay: Int,
    val condition: WeatherApiConditionDto,
    @Json(name = "wind_kph") val windKph: Double,
    @Json(name = "wind_degree") val windDegree: Int,
    @Json(name = "wind_dir") val windDir: String,
    @Json(name = "pressure_mb") val pressureMb: Double,
    @Json(name = "precip_mm") val precipMm: Double,
    val humidity: Int,
    @Json(name = "cloud") val cloud: Int,
    @Json(name = "feelslike_c") val feelslikeC: Double,
    @Json(name = "feelslike_f") val feelslikeF: Double,
    @Json(name = "windchill_c") val windchillC: Double,
    @Json(name = "heatindex_c") val heatindexC: Double,
    @Json(name = "dewpoint_c") val dewpointC: Double,
    @Json(name = "vis_km") val visKm: Double,
    val uv: Double,
    @Json(name = "gust_kph") val gustKph: Double,
    @Json(name = "air_quality") val airQuality: WeatherApiAirQualityDto? = null
)

@JsonClass(generateAdapter = true)
data class WeatherApiConditionDto(
    val text: String,
    val icon: String,
    val code: Int
)

// ─── Air Quality ──────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WeatherApiAirQualityDto(
    val co: Double? = null,
    val no2: Double? = null,
    val o3: Double? = null,
    val so2: Double? = null,
    @Json(name = "pm2_5") val pm25: Double? = null,
    val pm10: Double? = null,
    @Json(name = "us-epa-index") val usEpaIndex: Int? = null,
    @Json(name = "gb-defra-index") val gbDefraIndex: Int? = null
)

// ─── Forecast ─────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WeatherApiForecastDto(
    @Json(name = "forecastday") val forecastDay: List<WeatherApiForecastDayDto>
)

@JsonClass(generateAdapter = true)
data class WeatherApiForecastDayDto(
    val date: String,
    @Json(name = "date_epoch") val dateEpoch: Long,
    val day: WeatherApiDayDto,
    val astro: WeatherApiAstroDto,
    val hour: List<WeatherApiHourDto>
)

@JsonClass(generateAdapter = true)
data class WeatherApiDayDto(
    @Json(name = "maxtemp_c") val maxtempC: Double,
    @Json(name = "mintemp_c") val mintempC: Double,
    @Json(name = "avgtemp_c") val avgtempC: Double,
    @Json(name = "maxwind_kph") val maxwindKph: Double,
    @Json(name = "totalprecip_mm") val totalprecipMm: Double,
    @Json(name = "avghumidity") val avghumidity: Double,
    @Json(name = "daily_will_it_rain") val dailyWillItRain: Int,
    @Json(name = "daily_chance_of_rain") val dailyChanceOfRain: Int,
    @Json(name = "daily_will_it_snow") val dailyWillItSnow: Int,
    @Json(name = "daily_chance_of_snow") val dailyChanceOfSnow: Int,
    val condition: WeatherApiConditionDto,
    val uv: Double
)

@JsonClass(generateAdapter = true)
data class WeatherApiAstroDto(
    val sunrise: String,
    val sunset: String,
    val moonrise: String,
    val moonset: String,
    @Json(name = "moon_phase") val moonPhase: String
)

@JsonClass(generateAdapter = true)
data class WeatherApiHourDto(
    @Json(name = "time_epoch") val timeEpoch: Long,
    val time: String,
    @Json(name = "temp_c") val tempC: Double,
    @Json(name = "is_day") val isDay: Int,
    val condition: WeatherApiConditionDto,
    @Json(name = "wind_kph") val windKph: Double,
    @Json(name = "wind_degree") val windDegree: Int,
    @Json(name = "wind_dir") val windDir: String,
    @Json(name = "pressure_mb") val pressureMb: Double,
    @Json(name = "precip_mm") val precipMm: Double,
    val humidity: Int,
    val cloud: Int,
    @Json(name = "feelslike_c") val feelslikeC: Double,
    @Json(name = "windchill_c") val windchillC: Double,
    @Json(name = "heatindex_c") val heatindexC: Double,
    @Json(name = "dewpoint_c") val dewpointC: Double,
    @Json(name = "will_it_rain") val willItRain: Int,
    @Json(name = "chance_of_rain") val chanceOfRain: Int,
    @Json(name = "will_it_snow") val willItSnow: Int,
    @Json(name = "chance_of_snow") val chanceOfSnow: Int,
    @Json(name = "vis_km") val visKm: Double,
    @Json(name = "gust_kph") val gustKph: Double,
    @Json(name = "uv") val uv: Double,
    @Json(name = "air_quality") val airQuality: WeatherApiAirQualityDto? = null
)


