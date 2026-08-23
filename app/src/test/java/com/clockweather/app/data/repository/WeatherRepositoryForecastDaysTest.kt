package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.WeatherDataProvider
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherProviderType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherRepositoryForecastDaysTest {

    private val dataStore: DataStore<Preferences> = mockk()
    private val providerFactory: WeatherDataProviderFactory = mockk()
    private val database: WeatherDatabase = mockk(relaxed = true)
    private val currentWeatherDao: CurrentWeatherDao = mockk(relaxed = true)
    private val hourlyForecastDao: HourlyForecastDao = mockk(relaxed = true)
    private val dailyForecastDao: DailyForecastDao = mockk(relaxed = true)
    private val locationDao: LocationDao = mockk(relaxed = true)
    private val entityMapper: WeatherEntityMapper = mockk(relaxed = true)
    private val openMeteoProvider: WeatherDataProvider = mockk()
    private val googleProvider: WeatherDataProvider = mockk()

    private val repository = WeatherRepositoryImpl(
        dataStore = dataStore,
        providerFactory = providerFactory,
        database = database,
        currentWeatherDao = currentWeatherDao,
        hourlyForecastDao = hourlyForecastDao,
        dailyForecastDao = dailyForecastDao,
        locationDao = locationDao,
        entityMapper = entityMapper
    )

    private val location = Location(
        id = 1L,
        name = "Berlin",
        country = "DE",
        latitude = 52.52,
        longitude = 13.405
    )

    @Before
    fun setUp() {
        mockkObject(WeatherProviderPreferences)
        every { currentWeatherDao.getCurrentWeather(any()) } returns flowOf(null)
        every { hourlyForecastDao.getHourlyForecasts(any()) } returns flowOf(emptyList())
        every { dailyForecastDao.getDailyForecasts(any()) } returns flowOf(emptyList())
        every { locationDao.getLocationById(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        unmockkObject(WeatherProviderPreferences)
    }

    private fun setupProviderSelection(provider: WeatherProviderType) {
        every { WeatherProviderPreferences.resolve(any()) } returns provider
        every { WeatherProviderPreferences.defaultProvider() } returns provider
        every {
            dataStore.data
        } returns flowOf(
            preferencesOf(WeatherProviderPreferences.KEY_WEATHER_PROVIDER to provider.storageValue)
        )
    }

    private fun setupMissingProviderPreference() {
        every { dataStore.data } returns flowOf(emptyPreferences())
    }

    @Test
    fun `forceRefreshWeatherData passes forecastDays 14 to selected provider`() = runTest {
        setupProviderSelection(WeatherProviderType.OPEN_METEO)
        every { providerFactory.get(WeatherProviderType.OPEN_METEO) } returns openMeteoProvider
        coEvery { openMeteoProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("stop-after-provider-call")

        runCatching { repository.forceRefreshWeatherData(location, forecastDays = 14) }

        coVerify(exactly = 1) { openMeteoProvider.fetchWeatherData(location, 14, isNull()) }
    }

    @Test
    fun `forceRefreshWeatherData passes forecastDays 7 without hardcoding another value`() = runTest {
        setupProviderSelection(WeatherProviderType.OPEN_METEO)
        every { providerFactory.get(WeatherProviderType.OPEN_METEO) } returns openMeteoProvider
        coEvery { openMeteoProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("stop-after-provider-call")

        runCatching { repository.forceRefreshWeatherData(location, forecastDays = 7) }

        coVerify(exactly = 1) { openMeteoProvider.fetchWeatherData(location, 7, isNull()) }
    }

    @Test
    fun `forceRefreshWeatherData falls back to configured default provider when preference missing`() = runTest {
        val defaultProvider = WeatherProviderType.OPEN_METEO
        every { WeatherProviderPreferences.resolve(null) } returns defaultProvider
        every { WeatherProviderPreferences.defaultProvider() } returns defaultProvider
        val expectedForecastDays = 14.coerceIn(1, defaultProvider.maxForecastDays)
        val defaultDataProvider: WeatherDataProvider = mockk()
        setupMissingProviderPreference()
        every { providerFactory.get(defaultProvider) } returns defaultDataProvider
        coEvery { defaultDataProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("stop-after-provider-call")

        runCatching { repository.forceRefreshWeatherData(location, forecastDays = 14) }

        coVerify(exactly = 1) { defaultDataProvider.fetchWeatherData(location, expectedForecastDays, isNull()) }
    }

    @Test
    fun `forceRefreshWeatherData propagates error when selected provider fails`() = runTest {
        setupProviderSelection(WeatherProviderType.GOOGLE)
        every { providerFactory.get(WeatherProviderType.GOOGLE) } returns googleProvider
        every { providerFactory.get(WeatherProviderType.OPEN_METEO) } returns openMeteoProvider
        coEvery { googleProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("unauthorized")
        coEvery { openMeteoProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("unauthorized")

        val result = runCatching { repository.forceRefreshWeatherData(location, forecastDays = 14) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `forceRefreshWeatherData falls back to default provider when selected fails`() = runTest {
        val defaultProviderType = WeatherProviderType.OPEN_METEO
        val selectedProviderType = WeatherProviderType.GOOGLE
        every { WeatherProviderPreferences.resolve(any()) } returns selectedProviderType
        every { WeatherProviderPreferences.defaultProvider() } returns defaultProviderType

        val selectedProvider = googleProvider
        val defaultProvider = openMeteoProvider

        every {
            dataStore.data
        } returns flowOf(
            preferencesOf(WeatherProviderPreferences.KEY_WEATHER_PROVIDER to selectedProviderType.storageValue)
        )
        every { providerFactory.get(selectedProviderType) } returns selectedProvider
        every { providerFactory.get(defaultProviderType) } returns defaultProvider
        coEvery { selectedProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("unauthorized")
        coEvery { defaultProvider.fetchWeatherData(any(), any(), isNull()) } throws RuntimeException("stop-after-fallback-call")

        runCatching { repository.forceRefreshWeatherData(location, forecastDays = 14) }

        coVerify(atLeast = 1) {
            defaultProvider.fetchWeatherData(
                location,
                14.coerceIn(1, defaultProviderType.maxForecastDays),
                isNull()
            )
        }
    }
}
