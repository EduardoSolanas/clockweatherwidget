package com.clockweather.app.presentation.detail

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.usecase.GetWeatherDataUseCase
import com.clockweather.app.domain.usecase.RefreshWeatherUseCase
import com.clockweather.app.presentation.common.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var locationRepository: LocationRepository
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var getWeatherDataUseCase: GetWeatherDataUseCase
    private lateinit var refreshWeatherUseCase: RefreshWeatherUseCase
    private lateinit var context: Context
    private lateinit var app: ClockWeatherApplication
    private lateinit var powerManager: PowerManager

    private val location = Location(
        id = 1L,
        name = "London",
        country = "UK",
        latitude = 51.5072,
        longitude = -0.1276,
        isCurrentLocation = true,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        locationRepository = mockk()
        dataStore = mockk()
        getWeatherDataUseCase = mockk()
        refreshWeatherUseCase = mockk()
        context = mockk()
        app = mockk(relaxed = true)
        powerManager = mockk()
        every { context.applicationContext } returns app
        every { context.packageName } returns "com.clockweather.app"
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        // Location fully granted, so the setup banner state is not what these tests exercise.
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true

        every { dataStore.data } returns flowOf(
            preferencesOf(
                com.clockweather.app.presentation.settings.SettingsViewModel.KEY_FORECAST_DAYS to 7,
                booleanPreferencesKey("use_24h_clock") to true,
            ),
        )

        every { locationRepository.getSavedLocations() } returns flowOf(listOf(location))
        coEvery { locationRepository.getCurrentLocation() } returns null
        every { getWeatherDataUseCase(location) } returns flowOf(sampleWeatherData(location))
        coEvery { refreshWeatherUseCase.ensureFresh(location, forecastDays = 7) } just runs
        coEvery { refreshWeatherUseCase.forceRefresh(location, forecastDays = 7) } just runs
        coEvery { app.refreshAllWidgets(app) } just runs
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial detail refresh also refreshes widgets from shared cache`() = runTest(dispatcher) {
        WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )

        advanceUntilIdle()

        coVerify(exactly = 1) { refreshWeatherUseCase.ensureFresh(location, forecastDays = 7) }
        coVerify(exactly = 1) { app.refreshAllWidgets(app) }
    }

    @Test
    fun `refresh is throttled — second call within 5 minutes skips weather fetch and widget sync`() = runTest(dispatcher) {
        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )
        advanceUntilIdle() // initial load

        viewModel.refresh()
        advanceUntilIdle() // first manual refresh — allowed

        viewModel.refresh()
        advanceUntilIdle() // second manual refresh within 5 min — throttled

        coVerify(exactly = 1) { refreshWeatherUseCase.ensureFresh(location, forecastDays = 7) }
        coVerify(exactly = 1) { refreshWeatherUseCase.forceRefresh(location, forecastDays = 7) }
        coVerify(exactly = 2) { app.refreshAllWidgets(app) }
    }

    @Test
    fun `refresh is allowed again after 5-minute throttle window elapses`() = runTest(dispatcher) {
        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        // Simulate 5+ minutes elapsed by rewinding the recorded timestamp
        viewModel.lastRefreshTimeMs -= WeatherDetailViewModel.REFRESH_THROTTLE_MS

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshWeatherUseCase.ensureFresh(location, forecastDays = 7) }
        coVerify(exactly = 2) { refreshWeatherUseCase.forceRefresh(location, forecastDays = 7) }
    }

    @Test
    fun `manual detail refresh also refreshes widgets from shared cache`() = runTest(dispatcher) {
        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshWeatherUseCase.ensureFresh(location, forecastDays = 7) }
        coVerify(exactly = 1) { refreshWeatherUseCase.forceRefresh(location, forecastDays = 7) }
        coVerify(exactly = 2) { app.refreshAllWidgets(app) }
    }

    @Test
    fun `cancelling an in flight load does not show coroutine cancellation in UI`() = runTest(dispatcher) {
        every { getWeatherDataUseCase(location) } returns flow {
            delay(60_000)
            emit(sampleWeatherData(location))
        }

        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )

        viewModel.refresh()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        when (uiState) {
            is UiState.Error -> error("Expected cancellation to stay internal, but UI showed: ${uiState.message}")
            else -> Unit
        }
    }

    @Test
    fun `unrelated datastore preference changes do not trigger forceRefreshWeatherAndWidgets`() = runTest(dispatcher) {
        val prefFlow = kotlinx.coroutines.flow.MutableSharedFlow<Preferences>()
        every { dataStore.data } returns prefFlow

        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )
        advanceUntilIdle()

        // Initial emission with provider GOOGLE
        prefFlow.emit(preferencesOf(
            com.clockweather.app.presentation.settings.SettingsViewModel.KEY_WEATHER_PROVIDER to "GOOGLE",
            com.clockweather.app.presentation.settings.SettingsViewModel.KEY_TEMP_UNIT to "CELSIUS"
        ))
        advanceUntilIdle()

        // Unrelated emission: temp unit changed to FAHRENHEIT, provider remains GOOGLE
        prefFlow.emit(preferencesOf(
            com.clockweather.app.presentation.settings.SettingsViewModel.KEY_WEATHER_PROVIDER to "GOOGLE",
            com.clockweather.app.presentation.settings.SettingsViewModel.KEY_TEMP_UNIT to "FAHRENHEIT"
        ))
        advanceUntilIdle()

        // forceRefresh should NOT have been called for provider change
        coVerify(exactly = 0) { refreshWeatherUseCase.forceRefresh(any(), any()) }
    }

    @Test
    fun `failed manual refresh does not lock out user and permits immediate retry`() = runTest(dispatcher) {
        coEvery { refreshWeatherUseCase.forceRefresh(any(), any()) } throws RuntimeException("Network down")

        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshWeatherUseCase.forceRefresh(location, forecastDays = 7) }

        // Second immediate refresh should NOT be blocked by 5min throttle because previous attempt failed
        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { refreshWeatherUseCase.forceRefresh(location, forecastDays = 7) }
    }

    @Test
    fun `loadWeather emits cached weather data immediately before freshness check completes`() = runTest {
        coEvery { refreshWeatherUseCase.ensureFresh(any(), any()) } coAnswers {
            delay(5_000L)
        }

        val viewModel = WeatherDetailViewModel(
            getWeatherDataUseCase = getWeatherDataUseCase,
            refreshWeatherUseCase = refreshWeatherUseCase,
            locationRepository = locationRepository,
            dataStore = dataStore,
            context = context,
        )

        testScheduler.runCurrent()

        // Cache must be emitted immediately at t=0 despite refresh taking 5000ms
        val state = viewModel.uiState.value
        org.junit.Assert.assertTrue(
            "Expected UiState.Success with cached weather, but was $state",
            state is UiState.Success
        )

        advanceUntilIdle()
        coVerify(exactly = 1) { refreshWeatherUseCase.ensureFresh(location, 7) }
    }

    private fun sampleWeatherData(location: Location): WeatherData {
        return WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 17.0,
                feelsLikeTemperature = 17.0,
                humidity = 60,
                dewPoint = 9.0,
                precipitation = 0.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                windGusts = 12.0,
                visibility = 10.0,
                uvIndex = 5.0,
                cloudCover = 30,
                lastUpdated = LocalDateTime.of(2026, 4, 6, 10, 15),
            ),
            hourlyForecasts = emptyList(),
            dailyForecasts = listOf(
                DailyForecast(
                    date = LocalDate.of(2026, 4, 6),
                    weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                    temperatureMax = 20.0,
                    temperatureMin = 11.0,
                    feelsLikeMax = 20.0,
                    feelsLikeMin = 11.0,
                    sunrise = LocalTime.of(6, 0),
                    sunset = LocalTime.of(19, 0),
                    daylightDurationSeconds = 36000.0,
                    precipitationSum = 0.0,
                    precipitationProbability = 0,
                    windSpeedMax = 10.0,
                    windDirectionDominant = WindDirection.N,
                    windDirectionDegrees = 0,
                    uvIndexMax = 5.0,
                    averageHumidity = 60,
                    averagePressure = 1012.0,
                ),
            ),
        )
    }
}
