package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.work.testing.TestListenableWorkerBuilder
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The widget follows the user: once a new fix lands in a different place, the weather
 * cached for the previous one is worthless no matter how recently it was fetched.
 * Freshness alone must not decide the refresh — the cache is keyed by location row,
 * so a "fresh" entry can still hold the city the user left hours ago.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherRefreshAfterRelocationTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val testFile = File(context.filesDir, "worker_relocation_test.preferences_pb")
        testFile.delete()
        dataStore = PreferenceDataStoreFactory.create { testFile }
    }

    private fun runWorker(
        locationRepository: LocationRepository,
        weatherRepository: WeatherRepository,
    ) = TestListenableWorkerBuilder<WeatherUpdateWorker>(context)
        .setWorkerFactory(HiltWorkerFactoryStub(weatherRepository, locationRepository, dataStore))
        .build()

    @Test
    fun `moving to another city refetches instead of serving the previous city's cache`() = runTest {
        val locationRepository = FakeLocationRepository(saved = listOf(LONDON), detected = BRIGHTON_FIX)
        val weatherRepository = RecordingWeatherRepository()

        runWorker(locationRepository, weatherRepository).doWork()

        val brightonRow = BRIGHTON_FIX.copy(id = LONDON.id, isCurrentLocation = true)
        assertEquals(listOf(brightonRow), weatherRepository.forceRefreshCalls)
        assertEquals(emptyList<Location>(), weatherRepository.ensureFreshCalls)
    }

    @Test
    fun `the relocated coordinates are persisted so the widget label follows too`() = runTest {
        val locationRepository = FakeLocationRepository(saved = listOf(LONDON), detected = BRIGHTON_FIX)

        runWorker(locationRepository, RecordingWeatherRepository()).doWork()

        val brightonRow = BRIGHTON_FIX.copy(id = LONDON.id, isCurrentLocation = true)
        assertEquals(listOf(brightonRow), locationRepository.savedUpdates)
    }

    @Test
    fun `a jittery fix in the same place stays freshness gated`() = runTest {
        val jittered = LONDON.copy(id = 0L, latitude = 51.50755, longitude = -0.12805)
        val locationRepository = FakeLocationRepository(saved = listOf(LONDON), detected = jittered)
        val weatherRepository = RecordingWeatherRepository()

        runWorker(locationRepository, weatherRepository).doWork()

        assertEquals(emptyList<Location>(), weatherRepository.forceRefreshCalls)
        assertEquals(listOf(jittered.copy(id = LONDON.id)), weatherRepository.ensureFreshCalls)
    }

    @Test
    fun `a pinned location ignores where the user is and stays freshness gated`() = runTest {
        val locationRepository = FakeLocationRepository(saved = listOf(PARIS), detected = BRIGHTON_FIX)
        val weatherRepository = RecordingWeatherRepository()

        runWorker(locationRepository, weatherRepository).doWork()

        assertEquals(emptyList<Location>(), weatherRepository.forceRefreshCalls)
        assertEquals(listOf(PARIS), weatherRepository.ensureFreshCalls)
    }

    private class RecordingWeatherRepository : WeatherRepository {
        val ensureFreshCalls = mutableListOf<Location>()
        val forceRefreshCalls = mutableListOf<Location>()

        override fun getWeatherData(location: Location): Flow<WeatherData?> = flowOf(null)

        override suspend fun ensureFreshWeatherData(
            location: Location,
            forecastDays: Int,
            maxAgeMinutes: Long?,
        ) {
            ensureFreshCalls += location
        }

        override suspend fun forceRefreshWeatherData(location: Location, forecastDays: Int) {
            forceRefreshCalls += location
        }
    }

    private class FakeLocationRepository(
        saved: List<Location>,
        private val detected: Location?,
    ) : LocationRepository {
        private val locations = saved.toMutableList()
        val savedUpdates = mutableListOf<Location>()

        override fun getSavedLocations(): Flow<List<Location>> = flowOf(locations.toList())

        override suspend fun saveLocation(location: Location): Long {
            savedUpdates += location
            val index = locations.indexOfFirst { it.id == location.id }
            if (index >= 0) locations[index] = location else locations += location
            return location.id
        }

        override suspend fun deleteLocation(locationId: Long) {
            locations.removeAll { it.id == locationId }
        }

        override suspend fun getCurrentLocation(): Location? = detected

        override suspend fun searchLocations(query: String): List<Location> = emptyList()

        override fun getLocationById(id: Long): Flow<Location?> =
            flowOf(locations.firstOrNull { it.id == id })

        override fun getFallbackLocation(): Location = LONDON
    }

    private companion object {
        val LONDON = Location(
            id = 1L,
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true,
        )

        /** A fresh fix carries no database identity yet — the resolver grafts it on. */
        val BRIGHTON_FIX = Location(
            name = "Brighton",
            country = "GB",
            latitude = 50.8225,
            longitude = -0.1372,
            isCurrentLocation = true,
        )

        val PARIS = Location(
            id = 2L,
            name = "Paris",
            country = "FR",
            latitude = 48.8566,
            longitude = 2.3522,
            isCurrentLocation = false,
        )
    }
}
