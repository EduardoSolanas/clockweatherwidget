package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.GoogleAirQualityApi
import com.clockweather.app.data.remote.api.GooglePollenApi
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.PollenData
import com.clockweather.app.domain.model.PollenType
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Baseline for finding 9 of docs/TIME_WEATHER_SYNC_REVIEW.md: how many HTTP requests one
 * refresh actually costs, broken down by endpoint.
 *
 * Google bills per request, so the hourly pagination loop — not the number of worker runs —
 * is what drives the bill. These counts are asserted rather than printed so a change in
 * request volume fails here instead of appearing on an invoice.
 *
 * A real HTTP server serves every endpoint. Responses are routed by method and path rather
 * than queued: the provider issues its requests concurrently through `async`, so FIFO
 * queueing would hand responses to arbitrary callers.
 */
@RunWith(RobolectricTestRunner::class)
class RequestVolumeBaselineTest {

    private lateinit var server: MockWebServer
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** Hours the paginated Google endpoint returns per page; the API caps this at 24. */
    private val googleHourlyPageSize = 24

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                val key = "${request.method} $path"
                counts.getOrPut(key) { AtomicInteger() }.incrementAndGet()
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(bodyFor(request.method.orEmpty(), path, request.path.orEmpty()))
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Google seven day refresh with an empty cache`() = runTest {
        val weather = googleProvider().fetchWeatherData(location, forecastDays = 7, cachedData = null)

        assertEquals(7 * 24, weather.hourlyForecasts.size)
        assertEquals(7, weather.dailyForecasts.size)

        assertEquals(1, count("GET /v1/currentConditions:lookup"))
        assertEquals(7, count("GET /v1/forecast/hours:lookup"))
        assertEquals(1, count("GET /v1/forecast/days:lookup"))
        assertEquals(1, count("GET /v1/forecast:lookup"))
        assertEquals(1, count("GET /v1/air-quality"))
        assertEquals(1, count("POST /v1/currentConditions:lookup"))
        assertEquals(12, total())
        report("Google 7-day, empty cache")
    }

    @Test
    fun `Google ten day refresh with an empty cache`() = runTest {
        val weather = googleProvider().fetchWeatherData(location, forecastDays = 10, cachedData = null)

        assertEquals(10 * 24, weather.hourlyForecasts.size)

        assertEquals(10, count("GET /v1/forecast/hours:lookup"))
        assertEquals(15, total())
        report("Google 10-day, empty cache")
    }

    /**
     * The optional sections carry their own ages (finding 1), so a refresh that only needs
     * core weather must not re-buy air quality and pollen.
     */
    @Test
    fun `Google seven day refresh reuses fresh air quality and pollen`() = runTest {
        googleProvider().fetchWeatherData(location, forecastDays = 7, cachedData = freshOptionalCache())

        assertEquals(0, count("GET /v1/forecast:lookup"))
        assertEquals(0, count("POST /v1/currentConditions:lookup"))
        assertEquals(0, count("GET /v1/air-quality"))

        assertEquals(7, count("GET /v1/forecast/hours:lookup"))
        assertEquals(9, total())
        report("Google 7-day, fresh air quality and pollen")
    }

    @Test
    fun `Open-Meteo refresh costs the same at every forecast length`() = runTest {
        openMeteoProvider().fetchWeatherData(location, forecastDays = 7, cachedData = null)
        val sevenDay = total()
        counts.clear()
        openMeteoProvider().fetchWeatherData(location, forecastDays = 14, cachedData = null)

        assertEquals(2, sevenDay)
        assertEquals(2, total())
        assertEquals(1, count("GET /v1/forecast"))
        assertEquals(1, count("GET /v1/air-quality"))
        report("Open-Meteo 14-day, empty cache")
    }

    private fun count(key: String): Int = counts[key]?.get() ?: 0

    private fun total(): Int = counts.values.sumOf { it.get() }

    private fun report(scenario: String) {
        val lines = counts.entries.sortedBy { it.key }.joinToString("\n") { "  ${it.value.get()}  ${it.key}" }
        println("\n$scenario — ${total()} requests\n$lines")
    }

    private fun googleProvider() = GoogleWeatherProvider(
        googleWeatherApi = api(GoogleWeatherApi::class.java),
        googlePollenApi = api(GooglePollenApi::class.java),
        googleAirQualityApi = api(GoogleAirQualityApi::class.java),
        openMeteoAirQualityApi = api(OpenMeteoAirQualityApi::class.java),
        apiKey = "test-key",
        mapper = GoogleWeatherMapper()
    )

    private fun openMeteoProvider() = OpenMeteoWeatherProvider(
        openMeteoWeatherApi = api(OpenMeteoWeatherApi::class.java),
        openMeteoAirQualityApi = api(OpenMeteoAirQualityApi::class.java),
        mapper = WeatherDtoMapper()
    )

    private fun <T> api(service: Class<T>): T {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(service)
    }

    /**
     * Air quality and pollen recorded now, so both sit inside their TTLs
     * (see AIR_QUALITY_MAX_AGE_MINUTES and POLLEN_MAX_AGE_MINUTES).
     */
    private fun freshOptionalCache(): WeatherData {
        val now = LocalDateTime.now()
        return WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 12.0,
                feelsLikeTemperature = 11.0,
                humidity = 70,
                dewPoint = 7.0,
                precipitation = 0.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.CLEAR_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 8.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                windGusts = 12.0,
                visibility = 10.0,
                uvIndex = 1.0,
                cloudCover = 10,
                lastUpdated = now
            ),
            hourlyForecasts = emptyList(),
            dailyForecasts = pollenCoveredDays(now),
            airQuality = AirQuality(
                co = 200.0,
                no2 = 20.0,
                o3 = 60.0,
                so2 = 5.0,
                pm25 = 8.0,
                pm10 = 14.0,
                usEpaIndex = 2,
                gbDefraIndex = 3,
                lastUpdated = now
            ),
            pollenLastUpdated = now
        )
    }

    /**
     * isPollenFresh also requires enough days actually carrying pollen, so a cache that is
     * merely recent is not enough to prove reuse.
     */
    private fun pollenCoveredDays(now: LocalDateTime): List<DailyForecast> {
        val today = now.toLocalDate()
        return (0 until 5).map { offset ->
            DailyForecast(
                date = today.plusDays(offset.toLong()),
                weatherCondition = WeatherCondition.CLEAR_DAY,
                temperatureMax = 15.0,
                temperatureMin = 8.0,
                feelsLikeMax = 14.0,
                feelsLikeMin = 7.0,
                sunrise = LocalTime.of(6, 30),
                sunset = LocalTime.of(19, 30),
                daylightDurationSeconds = 46800.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 12.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 2.0,
                averageHumidity = 65,
                averagePressure = 1012.0,
                pollen = PollenData(
                    grassPollen = PollenType(code = "GRASS", displayName = "Grass", indexValue = 1)
                )
            )
        }
    }

    private fun bodyFor(method: String, path: String, fullPath: String): String = when {
        method == "POST" && path == "/v1/currentConditions:lookup" -> "{}"
        path == "/v1/currentConditions:lookup" -> "{}"
        path == "/v1/forecast/hours:lookup" -> hourlyPage(fullPath)
        path == "/v1/forecast/days:lookup" -> dailyPage(fullPath)
        path == "/v1/forecast:lookup" -> "{}"
        path == "/v1/air-quality" -> "{}"
        path == "/v1/forecast" -> openMeteoForecast()
        else -> "{}"
    }

    /** WeatherResponseDto declares these without defaults, so the envelope has to be complete. */
    private fun openMeteoForecast(): String = """
        {
          "latitude": 51.5,
          "longitude": -0.12,
          "elevation": 23.0,
          "generationtime_ms": 0.2,
          "utc_offset_seconds": 0,
          "timezone": "Europe/London",
          "timezone_abbreviation": "GMT",
          "current": {
            "time": "2026-09-06T09:00",
            "temperature_2m": 14.0,
            "relative_humidity_2m": 70,
            "apparent_temperature": 13.0,
            "precipitation": 0.0,
            "weather_code": 0,
            "cloud_cover": 10,
            "pressure_msl": 1012.0,
            "surface_pressure": 1010.0,
            "wind_speed_10m": 9.0,
            "wind_direction_10m": 180,
            "wind_gusts_10m": 15.0,
            "is_day": 1
          },
          "current_units": null,
          "hourly": null,
          "hourly_units": null,
          "daily": null,
          "daily_units": null
        }
    """.trimIndent()

    /**
     * Always returns a full page plus a continuation token, so the provider's loop runs
     * until it has collected the hours it asked for. That termination condition is what
     * the page-count assertions measure.
     */
    private fun hourlyPage(fullPath: String): String {
        val hours = (1..googleHourlyPageSize).joinToString(",") { "{}" }
        return """{"forecastHours":[$hours],"nextPageToken":"next-${fullPath.hashCode()}"}"""
    }

    private fun dailyPage(fullPath: String): String {
        val days = requestedDays(fullPath)
        val entries = (1..days).joinToString(",") { "{}" }
        return """{"forecastDays":[$entries]}"""
    }

    private fun requestedDays(fullPath: String): Int =
        Regex("[?&]days=(\\d+)").find(fullPath)?.groupValues?.get(1)?.toInt() ?: 7

    private val location = Location(
        id = 1L,
        name = "London",
        country = "GB",
        latitude = 51.5074,
        longitude = -0.1278,
        isCurrentLocation = true
    )
}
