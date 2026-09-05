package com.clockweather.app.data.repository

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
import com.clockweather.app.data.remote.api.OpenMeteoAirQualityApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.domain.model.Location
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WeatherRepositorySnapshotTest {
    private lateinit var database: WeatherDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries().build()
        dataStoreFile = File(context.cacheDir, "weather_snapshot_${System.nanoTime()}.preferences_pb")
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
    fun `getWeatherData reads coherent snapshot and preserves null section ages`() = runTest {
        val weatherLocation = Location(1L, "London", "GB", 51.5074, -0.1278, isCurrentLocation = true)
        database.locationDao().insertLocation(
            LocationEntity(1L, "Brighton", "GB", 50.8225, -0.1372, "Europe/London", true)
        )
        database.currentWeatherDao().insertCurrentWeather(
            CurrentWeatherEntity(
                locationId = 1L, temperature = 18.0, feelsLikeTemperature = 18.0, humidity = 50,
                dewPoint = 10.0, precipitation = 0.0, precipitationProbability = 0, weatherCode = 1,
                isDay = true, pressure = 1013.25, windSpeed = 10.0, windDirectionDegrees = 0,
                windGusts = 12.0, visibility = 10000.0, uvIndex = 3.0, cloudCover = 20,
                lastUpdated = "2026-09-05T10:00:00", locationName = "London", latitude = 51.5074,
                longitude = -0.1278, aqCo = 1.0, aqNo2 = 2.0, aqO3 = 3.0, aqSo2 = 4.0,
                aqPm25 = 5.0, aqPm10 = 6.0, aqUsEpaIndex = 1, aqGbDefraIndex = 1,
                aqLastUpdated = null, pollenLastUpdated = null
            )
        )
        val repository = WeatherRepositoryImpl(
            dataStore = dataStore, providerFactory = realProviderFactory(), database = database,
            currentWeatherDao = database.currentWeatherDao(), hourlyForecastDao = database.hourlyForecastDao(),
            dailyForecastDao = database.dailyForecastDao(), locationDao = database.locationDao(),
            entityMapper = WeatherEntityMapper()
        )

        val result = repository.getWeatherData(weatherLocation).first()!!

        assertEquals("London", result.location.name)
        assertEquals(51.5074, result.location.latitude, 0.001)
        assertEquals(18.0, result.currentWeather.temperature, 0.001)
        assertNull(result.airQuality?.lastUpdated)
        assertNull(result.pollenLastUpdated)
    }

    private fun realProviderFactory(): WeatherDataProviderFactory {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        fun <T> api(baseUrl: String, service: Class<T>): T = Retrofit.Builder().baseUrl(baseUrl)
            .client(OkHttpClient()).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(service)
        val openMeteoAirQuality = api(OpenMeteoAirQualityApi.BASE_URL, OpenMeteoAirQualityApi::class.java)
        val openMeteo = OpenMeteoWeatherProvider(
            api(OpenMeteoWeatherApi.BASE_URL, OpenMeteoWeatherApi::class.java), openMeteoAirQuality, WeatherDtoMapper()
        )
        val google = GoogleWeatherProvider(
            api(GoogleWeatherApi.BASE_URL, GoogleWeatherApi::class.java),
            api(GooglePollenApi.BASE_URL, GooglePollenApi::class.java),
            api(GoogleAirQualityApi.BASE_URL, GoogleAirQualityApi::class.java),
            openMeteoAirQuality, "", GoogleWeatherMapper()
        )
        return WeatherDataProviderFactory(openMeteo, google)
    }
}
