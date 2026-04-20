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
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * WeatherRepositoryImpl must delegate all refreshes through WeatherDataProvider.
 */
class WeatherRepositoryForecastDaysTest {

    private val dataStore: DataStore<Preferences> = mockk()
    private val providerFactory: WeatherDataProviderFactory = mockk()
    private val openMeteoProvider: WeatherDataProvider = mockk()
    private val openWeatherMapProvider: WeatherDataProvider = mockk()
    private val database: WeatherDatabase = mockk()
    private val currentWeatherDao: CurrentWeatherDao = mockk()
    private val hourlyForecastDao: HourlyForecastDao = mockk()
    private val dailyForecastDao: DailyForecastDao = mockk()
    private val locationDao: LocationDao = mockk()
    private val entityMapper: WeatherEntityMapper = mockk()

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
    private val expectedDefaultProvider = if (
        WeatherProviderPreferences.isConfigured(WeatherProviderType.OPENWEATHERMAP)
    ) {
        WeatherProviderType.OPENWEATHERMAP
    } else {
        WeatherProviderType.OPEN_METEO
    }

    private fun setupProviderSelection(provider: WeatherProviderType) {
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
    fun `refreshWeatherData passes forecastDays 14 to selected provider`() = runTest {
        setupProviderSelection(WeatherProviderType.OPEN_METEO)
        every { providerFactory.get(WeatherProviderType.OPEN_METEO) } returns openMeteoProvider
        coEvery { openMeteoProvider.fetchWeatherData(any(), any()) } throws RuntimeException("stop-after-provider-call")

        runCatching { repository.refreshWeatherData(location, forecastDays = 14) }

        coVerify(exactly = 1) { openMeteoProvider.fetchWeatherData(location, 14) }
    }

    @Test
    fun `refreshWeatherData passes forecastDays 7 without hardcoding another value`() = runTest {
        setupProviderSelection(WeatherProviderType.OPEN_METEO)
        every { providerFactory.get(WeatherProviderType.OPEN_METEO) } returns openMeteoProvider
        coEvery { openMeteoProvider.fetchWeatherData(any(), any()) } throws RuntimeException("stop-after-provider-call")

        runCatching { repository.refreshWeatherData(location, forecastDays = 7) }

        coVerify(exactly = 1) { openMeteoProvider.fetchWeatherData(location, 7) }
    }

    @Test
    fun `refreshWeatherData falls back to configured default provider when preference missing`() = runTest {
        setupMissingProviderPreference()
        val expectedProvider = when (expectedDefaultProvider) {
            WeatherProviderType.OPENWEATHERMAP -> openWeatherMapProvider
            WeatherProviderType.OPEN_METEO -> openMeteoProvider
            else -> error("Unexpected default provider in test: $expectedDefaultProvider")
        }
        every { providerFactory.get(expectedDefaultProvider) } returns expectedProvider
        coEvery { expectedProvider.fetchWeatherData(any(), any()) } throws RuntimeException("stop-after-provider-call")

        runCatching { repository.refreshWeatherData(location, forecastDays = 14) }

        val expectedForecastDays = if (expectedDefaultProvider == WeatherProviderType.OPENWEATHERMAP) 8 else 14
        coVerify(exactly = 1) { expectedProvider.fetchWeatherData(location, expectedForecastDays) }
    }
}
