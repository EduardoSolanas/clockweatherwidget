package com.clockweather.app.data.provider

import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.remote.api.GoogleAirQualityApi
import com.clockweather.app.data.remote.api.GooglePollenApi
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.Location
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Finding 6 of docs/TIME_WEATHER_SYNC_REVIEW.md, with its claim narrowed by review.
 *
 * The finding was that broad `runCatching` handlers around the optional air-quality and pollen
 * calls swallow [kotlinx.coroutines.CancellationException]. They do — but mutation testing
 * showed this test cannot fail on that: `fetchWeatherData` is a `coroutineScope` that reaches
 * its results through `await()`, and the builder rethrows on a cancelled Job whatever the body
 * does. Wrapping the entire body in `catch (t: Throwable)` and returning a default still leaves
 * these tests green.
 *
 * So this does not prove the handlers are cancellation-safe; structured concurrency makes them
 * irrelevant. What it does guard is the narrower and still real regression of moving those
 * fetches somewhere cancellation does not reach — `GlobalScope`, `NonCancellable`, or a
 * detached scope — which would make a cancelled refresh keep running and keep spending
 * requests. Read it as protecting the shape of the concurrency, not the catch blocks.
 *
 * The required endpoints answer immediately here and only the optional ones hang, so a fetch
 * that completes after cancellation has escaped its scope.
 */
@RunWith(RobolectricTestRunner::class)
class ProviderCancellationTest {

    private lateinit var server: MockWebServer

    /** Requests that must never return, so cancellation is the only way out of them. */
    private val optionalPaths = setOf("/v1/forecast:lookup", "/v1/air-quality")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                val isGoogleAirQuality = request.method == "POST"
                if (isGoogleAirQuality || path in optionalPaths) {
                    return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                }
                return MockResponse().setResponseCode(200).setBody(bodyFor(path))
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `cancelling a Google refresh stops it instead of completing without optional data`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val provider = GoogleWeatherProvider(
            googleWeatherApi = api(GoogleWeatherApi::class.java),
            googlePollenApi = api(GooglePollenApi::class.java),
            googleAirQualityApi = api(GoogleAirQualityApi::class.java),
            openMeteoAirQualityApi = api(OpenMeteoAirQualityApi::class.java),
            apiKey = "test-key",
            mapper = GoogleWeatherMapper()
        )

        var completed = false
        val job = scope.launch {
            provider.fetchWeatherData(london, forecastDays = 7, cachedData = null)
            completed = true
        }
        delay(500)
        job.cancel()
        delay(500)

        assertFalse(
            "the fetch completed after cancellation, so an optional request swallowed it",
            completed
        )
        scope.cancel()
    }

    @Test
    fun `cancelling an Open-Meteo refresh stops it instead of completing without optional data`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val provider = OpenMeteoWeatherProvider(
            openMeteoWeatherApi = api(OpenMeteoWeatherApi::class.java),
            openMeteoAirQualityApi = api(OpenMeteoAirQualityApi::class.java),
            mapper = WeatherDtoMapper()
        )

        var completed = false
        val job = scope.launch {
            provider.fetchWeatherData(london, forecastDays = 7, cachedData = null)
            completed = true
        }
        delay(500)
        job.cancel()
        delay(500)

        assertFalse(completed)
        scope.cancel()
    }

    private fun bodyFor(path: String): String = when (path) {
        // A single page with no continuation token, so the pagination loop ends immediately.
        "/v1/forecast/hours:lookup" -> """{"forecastHours":[${(1..24).joinToString(",") { "{}" }}]}"""
        "/v1/forecast/days:lookup" -> """{"forecastDays":[${(1..7).joinToString(",") { "{}" }}]}"""
        "/v1/forecast" -> openMeteoForecast
        else -> "{}"
    }

    private val openMeteoForecast = """
        {
          "latitude": 51.5, "longitude": -0.12, "elevation": 23.0,
          "generationtime_ms": 0.2, "utc_offset_seconds": 0,
          "timezone": "Europe/London", "timezone_abbreviation": "GMT",
          "current": {
            "time": "2026-09-06T09:00", "temperature_2m": 14.0, "relative_humidity_2m": 70,
            "apparent_temperature": 13.0, "precipitation": 0.0, "weather_code": 0,
            "cloud_cover": 10, "pressure_msl": 1012.0, "surface_pressure": 1010.0,
            "wind_speed_10m": 9.0, "wind_direction_10m": 180, "wind_gusts_10m": 15.0, "is_day": 1
          },
          "current_units": null, "hourly": null, "hourly_units": null,
          "daily": null, "daily_units": null
        }
    """.trimIndent()

    private fun <T> api(service: Class<T>): T {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(service)
    }

    private val london = Location(
        id = 1L,
        name = "London",
        country = "GB",
        latitude = 51.5074,
        longitude = -0.1278,
        isCurrentLocation = true
    )
}
