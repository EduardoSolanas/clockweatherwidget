package com.clockweather.app.presentation.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import com.clockweather.app.worker.WeatherUpdateScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsWeatherRefreshIntervalTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var locationRepository: LocationRepository
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var dataStoreFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        locationRepository = mockk()
        weatherRepository = mockk()
        every { locationRepository.getSavedLocations() } returns flowOf(emptyList())
        mockkObject(WeatherUpdateScheduler)
        every { WeatherUpdateScheduler.schedule(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(WeatherUpdateScheduler)
        Dispatchers.resetMain()
        if (::dataStoreFile.isInitialized) dataStoreFile.delete()
    }

    @Test
    fun `setWeatherRefreshInterval stores active weather refresh key and reschedules`() = runTest(dispatcher) {
        dataStoreFile = File(context.filesDir, "settings-refresh-interval-test.preferences_pb").apply {
            delete()
        }
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        val viewModel = SettingsViewModel(
            locationRepository = locationRepository,
            weatherRepository = weatherRepository,
            dataStore = dataStore,
            context = context,
        )

        viewModel.setWeatherRefreshInterval(2)
        advanceUntilIdle()

        verify(timeout = 1_000, exactly = 1) { WeatherUpdateScheduler.schedule(context, 15) }
        assertEquals(15, dataStore.data.first()[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL])
    }
}
