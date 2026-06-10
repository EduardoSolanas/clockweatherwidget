package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WeatherUpdateWorkerPartialFailureTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var locationRepository: LocationRepository

    private val locationA = Location(id = 1L, name = "Paris", country = "FR", latitude = 48.85, longitude = 2.35)
    private val locationB = Location(id = 2L, name = "Berlin", country = "DE", latitude = 52.52, longitude = 13.4)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val testFile = File(context.filesDir, "worker_partial_failure_test.preferences_pb")
        testFile.delete()
        dataStore = PreferenceDataStoreFactory.create { testFile }
        weatherRepository = mockk()
        locationRepository = mockk()
    }

    private fun buildWorker(runAttempt: Int = 0): WeatherUpdateWorker =
        TestListenableWorkerBuilder<WeatherUpdateWorker>(context, runAttemptCount = runAttempt)
            .setWorkerFactory(
                HiltWorkerFactoryStub(weatherRepository, locationRepository, dataStore)
            )
            .build()

    @Test
    fun `returns retry when one location fails on first attempt`() = runTest {
        coEvery { locationRepository.getSavedLocations() } returns flowOf(listOf(locationA, locationB))
        coJustRun { weatherRepository.ensureFreshWeatherData(locationA, any()) }
        coEvery { weatherRepository.ensureFreshWeatherData(locationB, any()) } throws RuntimeException("network error")

        val result = buildWorker(runAttempt = 0).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `returns failure when one location fails on third attempt`() = runTest {
        coEvery { locationRepository.getSavedLocations() } returns flowOf(listOf(locationA, locationB))
        coJustRun { weatherRepository.ensureFreshWeatherData(locationA, any()) }
        coEvery { weatherRepository.ensureFreshWeatherData(locationB, any()) } throws RuntimeException("network error")

        val result = buildWorker(runAttempt = 3).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `still fetches locationA on retry even when locationA previously succeeded`() = runTest {
        coEvery { locationRepository.getSavedLocations() } returns flowOf(listOf(locationA, locationB))
        // On retry: locationA is now fresh (ensureFresh is a no-op), locationB still fails.
        coJustRun { weatherRepository.ensureFreshWeatherData(locationA, any()) }
        coEvery { weatherRepository.ensureFreshWeatherData(locationB, any()) } throws RuntimeException("still down")

        val result = buildWorker(runAttempt = 1).doWork()

        // ensureFreshWeatherData for A is called (but skips network if data is fresh)
        coVerify(exactly = 1) { weatherRepository.ensureFreshWeatherData(locationA, any()) }
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `returns success when all locations succeed`() = runTest {
        coEvery { locationRepository.getSavedLocations() } returns flowOf(listOf(locationA, locationB))
        coJustRun { weatherRepository.ensureFreshWeatherData(any(), any()) }

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
