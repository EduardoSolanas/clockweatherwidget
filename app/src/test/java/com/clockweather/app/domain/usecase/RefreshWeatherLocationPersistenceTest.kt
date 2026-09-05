package com.clockweather.app.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.local.entity.LocationEntity
import com.clockweather.app.data.mapper.GoogleWeatherMapper
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.GoogleWeatherProvider
import com.clockweather.app.data.provider.OpenMeteoWeatherProvider
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.remote.api.GoogleAirQualityApi
import com.clockweather.app.data.remote.api.GooglePollenApi
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.api.NominatimReverseGeocodingApi
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoGeocodingApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.data.repository.LocationRepositoryImpl
import com.clockweather.app.data.repository.WeatherRepositoryImpl
import com.clockweather.app.domain.model.Location
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RefreshWeatherLocationPersistenceTest {
    private lateinit var database: WeatherDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreFile = File(context.cacheDir, "refresh_location_${System.nanoTime()}.preferences_pb")
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `failed refresh leaves migrated current location row unchanged`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val original = LocationEntity(
            id = 1L,
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            timezone = "Europe/London",
            isCurrentLocation = true,
        )
        database.locationDao().insertLocation(original)
        database.currentWeatherDao().insertCurrentWeather(migratedCurrentWeather(original.id))

        val entityMapper = WeatherEntityMapper()
        val locationRepository = LocationRepositoryImpl(
            context = context,
            locationDao = database.locationDao(),
            geocodingApi = api(OpenMeteoGeocodingApi::class.java),
            reverseGeocodingApi = api(NominatimReverseGeocodingApi::class.java),
            entityMapper = entityMapper,
            dtoMapper = WeatherDtoMapper(),
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context),
        )
        val weatherRepository = WeatherRepositoryImpl(
            dataStore = dataStore,
            providerFactory = realProviderFactory(),
            database = database,
            currentWeatherDao = database.currentWeatherDao(),
            hourlyForecastDao = database.hourlyForecastDao(),
            dailyForecastDao = database.dailyForecastDao(),
            locationDao = database.locationDao(),
            entityMapper = entityMapper,
        )
        val moved = Location(
            id = original.id,
            name = "Berlin",
            country = "DE",
            latitude = 52.5200,
            longitude = 13.4050,
            isCurrentLocation = true,
        )

        val refreshResult = runCatching {
            RefreshWeatherUseCase(weatherRepository).forceRefreshThenSaveLocation(
                location = moved,
                forecastDays = 7,
                locationRepository = locationRepository,
            )
        }
        assertTrue("Expected the unavailable endpoint to fail", refreshResult.isFailure)
        assertTrue("Expected a real HTTP connection failure", refreshResult.exceptionOrNull() is java.io.IOException)

        assertEquals(original, database.locationDao().getLocationById(original.id).first())
        val cached = weatherRepository.getWeatherData(moved).first()!!
        assertEquals("London", cached.location.name)
        assertEquals(12.0, cached.currentWeather.temperature, 0.001)
    }

    private fun migratedCurrentWeather(locationId: Long) = CurrentWeatherEntity(
        locationId = locationId,
        temperature = 12.0,
        feelsLikeTemperature = 11.0,
        humidity = 70,
        dewPoint = 7.0,
        precipitation = 0.0,
        precipitationProbability = 0,
        weatherCode = 1,
        isDay = true,
        pressure = 1013.0,
        windSpeed = 8.0,
        windDirectionDegrees = 180,
        windGusts = 12.0,
        visibility = 10_000.0,
        uvIndex = 2.0,
        cloudCover = 20,
        lastUpdated = "2026-09-05T10:00:00",
        locationName = null,
        latitude = null,
        longitude = null,
    )

    private fun realProviderFactory(): WeatherDataProviderFactory {
        val airQualityApi = api(OpenMeteoAirQualityApi::class.java)
        val openMeteo = OpenMeteoWeatherProvider(
            api(OpenMeteoWeatherApi::class.java),
            airQualityApi,
            WeatherDtoMapper(),
        )
        val google = GoogleWeatherProvider(
            api(GoogleWeatherApi::class.java),
            api(GooglePollenApi::class.java),
            api(GoogleAirQualityApi::class.java),
            airQualityApi,
            "",
            GoogleWeatherMapper(),
        )
        return WeatherDataProviderFactory(openMeteo, google)
    }

    private fun <T> api(service: Class<T>): T {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/")
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(service)
    }
}
