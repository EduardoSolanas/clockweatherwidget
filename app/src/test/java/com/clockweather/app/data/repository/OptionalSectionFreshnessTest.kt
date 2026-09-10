package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.local.entity.DailyForecastEntity
import com.clockweather.app.data.local.entity.HourlyForecastEntity
import com.clockweather.app.data.local.entity.LocationEntity
import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.GoogleWeatherProvider
import com.clockweather.app.data.provider.OpenMeteoWeatherProvider
import com.clockweather.app.data.provider.RefreshScope
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.data.remote.api.GoogleAirQualityApi
import com.clockweather.app.data.remote.api.GooglePollenApi
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherProviderType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finding S5 of refresh_improvements.md.
 *
 * `ensureFreshWeatherData` used to decide freshness from core weather and forecast coverage
 * alone, so a warm resume could sit behind fresh current conditions with an expired air quality
 * reading and never ask for more. Every case here seeds core weather, hourly and daily coverage
 * that the old predicate already considered fresh, so the request count observes the optional
 * sections and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
class OptionalSectionFreshnessTest {

    private lateinit var database: WeatherDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var server: MockWebServer
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    private val brighton = Location(1L, "Brighton", "GB", 50.8225, -0.1372, isCurrentLocation = true)
    private val forecastDays = 7

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries().build()
        dataStoreFile = File(context.cacheDir, "optional_sections_${System.nanoTime()}.preferences_pb")
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        dataStore.edit {
            it[WeatherProviderPreferences.KEY_WEATHER_PROVIDER] = WeatherProviderType.OPEN_METEO.storageValue
        }

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                val body = if (path == "/v1/forecast") openMeteoForecast else "{}"
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
        server.shutdown()
    }

    @Test
    fun `expired air quality refreshes a cache the core predicate calls fresh`() = runTest {
        seedCache(airQualityAgeMinutes = 90, pollenAgeMinutes = 0)

        repository().ensureFreshWeatherData(brighton, forecastDays, scope = RefreshScope.FOREGROUND)

        assertEquals(
            "air quality past its 60-minute TTL must not hide behind fresh current weather",
            1,
            count("/v1/forecast"),
        )
    }

    @Test
    fun `air quality never fetched at all refreshes on the first foreground request`() = runTest {
        seedCache(airQualityAgeMinutes = null, pollenAgeMinutes = 0)

        repository().ensureFreshWeatherData(brighton, forecastDays, scope = RefreshScope.FOREGROUND)

        assertEquals(
            "background work buys no air quality, so the app must fetch it on opening",
            1,
            count("/v1/forecast"),
        )
    }

    @Test
    fun `fresh optional sections leave a fresh cache alone`() = runTest {
        seedCache(airQualityAgeMinutes = 5, pollenAgeMinutes = 5)

        repository().ensureFreshWeatherData(brighton, forecastDays, scope = RefreshScope.FOREGROUND)

        assertEquals("nothing was due; no request should have been made", 0, count("/v1/forecast"))
    }

    /** The savings from scoping background work must survive this change. */
    @Test
    fun `background scope is not held stale by sections no widget displays`() = runTest {
        seedCache(airQualityAgeMinutes = 90, pollenAgeMinutes = 600)

        repository().ensureFreshWeatherData(
            brighton,
            forecastDays,
            scope = RefreshScope.background(pollenShownInWidget = false),
        )

        assertEquals(
            "widgets show neither section, so neither may trigger background work",
            0,
            count("/v1/forecast"),
        )
    }

    /**
     * The loop guard. A section the provider does not publish here would otherwise read as
     * permanently missing and enqueue a refresh on every single check.
     */
    @Test
    fun `a section that comes back empty still records when it was asked`() = runTest {
        seedCache(airQualityAgeMinutes = null, pollenAgeMinutes = null)

        repository().ensureFreshWeatherData(brighton, forecastDays, scope = RefreshScope.FOREGROUND)

        val row = database.currentWeatherDao().getCurrentWeather(1L).first()
        assertNull("the mock server returns no usable air quality", row?.aqUsEpaIndex)
        assertNotNull(
            "an unanswerable section must still be marked as asked, or it re-fetches forever",
            row?.aqLastUpdated,
        )
    }

    private fun count(path: String) = counts[path]?.get() ?: 0

    /**
     * Seeds a cache the pre-S5 predicate considered entirely fresh: current conditions from this
     * minute, 25 hours from the current hour, and [forecastDays] of daily coverage. A null age
     * means the section was never recorded, which is what background-only refreshes leave behind.
     */
    private suspend fun seedCache(airQualityAgeMinutes: Long?, pollenAgeMinutes: Long?) {
        val now = LocalDateTime.now()
        val stamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        fun ageStamp(minutes: Long?) = minutes
            ?.let { now.minusMinutes(it).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

        database.locationDao().insertLocation(
            LocationEntity(1L, "Brighton", "GB", brighton.latitude, brighton.longitude, "Europe/London", true)
        )
        database.currentWeatherDao().insertCurrentWeather(
            CurrentWeatherEntity(
                locationId = 1L, temperature = 18.0, feelsLikeTemperature = 18.0, humidity = 50,
                dewPoint = 10.0, precipitation = 0.0, precipitationProbability = 0, weatherCode = 1,
                isDay = true, pressure = 1013.25, windSpeed = 10.0, windDirectionDegrees = 0,
                windGusts = 12.0, visibility = 10000.0, uvIndex = 3.0, cloudCover = 20,
                lastUpdated = stamp,
                locationName = "Brighton",
                latitude = brighton.latitude, longitude = brighton.longitude,
                aqCo = airQualityAgeMinutes?.let { 1.0 },
                aqNo2 = airQualityAgeMinutes?.let { 2.0 },
                aqO3 = airQualityAgeMinutes?.let { 3.0 },
                aqSo2 = airQualityAgeMinutes?.let { 4.0 },
                aqPm25 = airQualityAgeMinutes?.let { 5.0 },
                aqPm10 = airQualityAgeMinutes?.let { 6.0 },
                aqUsEpaIndex = airQualityAgeMinutes?.let { 1 },
                aqGbDefraIndex = airQualityAgeMinutes?.let { 1 },
                aqLastUpdated = ageStamp(airQualityAgeMinutes),
                pollenLastUpdated = ageStamp(pollenAgeMinutes)
            )
        )

        // 24 future hours starting exactly at the current hour is what the foreground predicate
        // demands; seeding one more keeps the test off that boundary.
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        database.hourlyForecastDao().insertHourlyForecasts(
            (0 until 25).map { offset ->
                HourlyForecastEntity(
                    locationId = 1L,
                    dateTime = currentHour.plusHours(offset.toLong())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    temperature = 15.0, feelsLike = 15.0, humidity = 60, dewPoint = 8.0,
                    precipitationProbability = 0, weatherCode = 1, isDay = true,
                    pressure = 1013.0, windSpeed = 5.0, windDirectionDegrees = 0,
                    visibility = 10000.0, uvIndex = 2.0
                )
            }
        )

        val today = LocalDate.now()
        database.dailyForecastDao().insertDailyForecasts(
            (0 until forecastDays).map { offset ->
                DailyForecastEntity(
                    locationId = 1L,
                    date = today.plusDays(offset.toLong()).toString(),
                    weatherCode = 1, temperatureMax = 20.0, temperatureMin = 12.0,
                    feelsLikeMax = 20.0, feelsLikeMin = 12.0, sunrise = "06:30", sunset = "19:30",
                    daylightDurationSeconds = 46800.0, precipitationSum = 0.0,
                    precipitationProbability = 0, windSpeedMax = 12.0, windDirectionDegrees = 0,
                    uvIndexMax = 3.0, averageHumidity = 60, averagePressure = 1013.0,
                    pollenGrassIndex = 2, pollenGrassCategory = "Low"
                )
            }
        )
    }

    private fun repository() = WeatherRepositoryImpl(
        dataStore = dataStore,
        providerFactory = providerFactory(),
        database = database,
        currentWeatherDao = database.currentWeatherDao(),
        hourlyForecastDao = database.hourlyForecastDao(),
        dailyForecastDao = database.dailyForecastDao(),
        locationDao = database.locationDao(),
        entityMapper = WeatherEntityMapper()
    )

    private fun providerFactory(): WeatherDataProviderFactory {
        val openMeteoAirQuality = api(OpenMeteoAirQualityApi::class.java)
        val openMeteo = OpenMeteoWeatherProvider(
            api(OpenMeteoWeatherApi::class.java), openMeteoAirQuality, WeatherDtoMapper()
        )
        val google = GoogleWeatherProvider(
            api(GoogleWeatherApi::class.java), api(GooglePollenApi::class.java),
            api(GoogleAirQualityApi::class.java), openMeteoAirQuality, "", GoogleWeatherMapper()
        )
        return WeatherDataProviderFactory(openMeteo, google)
    }

    private fun <T> api(service: Class<T>): T {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(service)
    }

    private val openMeteoForecast = """
        {
          "latitude": 50.82, "longitude": -0.13, "elevation": 10.0,
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
}
