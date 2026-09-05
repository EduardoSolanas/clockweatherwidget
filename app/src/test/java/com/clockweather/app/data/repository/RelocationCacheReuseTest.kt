package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.local.entity.DailyForecastEntity
import com.clockweather.app.data.local.entity.LocationEntity
import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.GoogleWeatherProvider
import com.clockweather.app.data.provider.OpenMeteoWeatherProvider
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
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Queue step 3 of docs/TIME_WEATHER_SYNC_REVIEW.md, end to end.
 *
 * [CachedWeatherOwnershipTest] covers the decision; this covers the path through real Room and
 * a real HTTP server. Open-Meteo skips its air-quality request only when pollen and air quality
 * are both fresh in the cache it is given, so that request is a direct observation of whether
 * the cache was offered at all.
 */
@RunWith(RobolectricTestRunner::class)
class RelocationCacheReuseTest {

    private lateinit var database: WeatherDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var server: MockWebServer
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    private val brighton = Location(1L, "Brighton", "GB", 50.8225, -0.1372, isCurrentLocation = true)
    private val londonLatitude = 51.5074
    private val londonLongitude = -0.1278

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries().build()
        dataStoreFile = File(context.cacheDir, "relocation_${System.nanoTime()}.preferences_pb")
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
    fun `optional sections cached at the previous city are not reused after a move`() = runTest {
        seedCache(snapshotLatitude = londonLatitude, snapshotLongitude = londonLongitude)

        repository().forceRefreshWeatherData(brighton, forecastDays = 7)

        assertEquals(
            "London air quality and pollen must not be reused for Brighton",
            1,
            count("/v1/air-quality"),
        )
    }

    @Test
    fun `optional sections cached at the same city are reused`() = runTest {
        seedCache(snapshotLatitude = brighton.latitude, snapshotLongitude = brighton.longitude)

        repository().forceRefreshWeatherData(brighton, forecastDays = 7)

        assertEquals(
            "unmoved fresh sections must not be re-bought",
            0,
            count("/v1/air-quality"),
        )
    }

    /** Rows written before the coordinate columns existed cannot establish ownership. */
    @Test
    fun `optional sections with unknown coordinates are not reused`() = runTest {
        seedCache(snapshotLatitude = null, snapshotLongitude = null)

        repository().forceRefreshWeatherData(brighton, forecastDays = 7)

        assertEquals(1, count("/v1/air-quality"))
    }

    private fun count(path: String) = counts[path]?.get() ?: 0

    private suspend fun seedCache(snapshotLatitude: Double?, snapshotLongitude: Double?) {
        val now = LocalDateTime.now()
        val stamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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
                locationName = "Cached city",
                latitude = snapshotLatitude, longitude = snapshotLongitude,
                aqCo = 1.0, aqNo2 = 2.0, aqO3 = 3.0, aqSo2 = 4.0, aqPm25 = 5.0, aqPm10 = 6.0,
                aqUsEpaIndex = 1, aqGbDefraIndex = 1,
                aqLastUpdated = stamp, pollenLastUpdated = stamp
            )
        )
        // isPollenFresh also needs enough covered days actually carrying pollen.
        val today = LocalDate.now()
        database.dailyForecastDao().insertDailyForecasts(
            (0 until 5).map { offset ->
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
