package com.clockweather.app.presentation.detail

import android.content.Context
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
        coEvery { refreshWeatherUseCase(location, forecastDays = 7) } just runs
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

        coVerify(exactly = 1) { refreshWeatherUseCase(location, forecastDays = 7) }
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

        // Only 2 calls total: 1 initial + 1 manual; second manual must be suppressed
        coVerify(exactly = 2) { refreshWeatherUseCase(location, forecastDays = 7) }
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

        coVerify(exactly = 3) { refreshWeatherUseCase(location, forecastDays = 7) }
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

        coVerify(exactly = 2) { refreshWeatherUseCase(location, forecastDays = 7) }
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
