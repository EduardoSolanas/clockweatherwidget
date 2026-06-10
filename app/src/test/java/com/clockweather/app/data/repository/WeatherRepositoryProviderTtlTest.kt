package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.local.entity.DailyForecastEntity
import com.clockweather.app.data.local.entity.HourlyForecastEntity
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.WeatherDataProvider
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherProviderType
import com.clockweather.app.domain.model.WindDirection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * ensureFreshWeatherData must use the selected provider's currentMaxAgeMinutes
 * as the freshness TTL: 12-minute-old data is fresh under Open-Meteo's 15-min
 * TTL (it would be stale under the 10-min default), and stale past 15 minutes.
 */
class WeatherRepositoryProviderTtlTest {

    private val dataStore: DataStore<Preferences> = mockk()
    private val providerFactory: WeatherDataProviderFactory = mockk()
    private val provider: WeatherDataProvider = mockk()
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

    private fun setupCachedWeather(ageMinutes: Long) {
        val now = LocalDateTime.now()
        val hourlyEntities = List(24) { mockk<HourlyForecastEntity>() }
        val dailyEntities = List(14) { mockk<DailyForecastEntity>() }
        every { currentWeatherDao.getCurrentWeather(location.id) } returns
            flowOf(mockk<CurrentWeatherEntity>())
        every { hourlyForecastDao.getHourlyForecasts(location.id) } returns flowOf(hourlyEntities)
        every { dailyForecastDao.getDailyForecasts(location.id) } returns flowOf(dailyEntities)
        every { locationDao.getLocationById(location.id) } returns flowOf(null)
        every { entityMapper.mapCurrentWeatherToDomain(any()) } returns
            sampleCurrentWeather(lastUpdated = now.minusMinutes(ageMinutes))
        every { entityMapper.mapAirQualityFromEntity(any()) } returns null
        every { entityMapper.mapHourlyToDomain(any()) } returnsMany
            hourlyForecastsFrom(now, count = 24)
        every { entityMapper.mapDailyToDomain(any()) } returnsMany
            dailyForecastsFrom(now.toLocalDate(), count = 14)
    }

    @Before
    fun mockProviderPreferences() {
        // Real resolve() consults BuildConfig API keys and silently falls back to
        // the default provider; mock it so the selected provider is always honored.
        mockkObject(WeatherProviderPreferences)
    }

    @After
    fun unmockProviderPreferences() {
        unmockkObject(WeatherProviderPreferences)
    }

    private fun setupProviderSelection(providerType: WeatherProviderType) {
        every { WeatherProviderPreferences.resolve(any()) } returns providerType
        every { WeatherProviderPreferences.defaultProvider() } returns providerType
        every { dataStore.data } returns flowOf(
            preferencesOf(WeatherProviderPreferences.KEY_WEATHER_PROVIDER to providerType.storageValue)
        )
        every { providerFactory.get(any()) } returns provider
        coEvery { provider.fetchWeatherData(any(), any()) } throws
            RuntimeException("stop-after-provider-call")
    }

    @Test
    fun `12-minute-old data is fresh for Open-Meteo and triggers no fetch`() = runTest {
        setupProviderSelection(WeatherProviderType.OPEN_METEO)
        setupCachedWeather(ageMinutes = 12)

        repository.ensureFreshWeatherData(location, forecastDays = 7)

        coVerify(exactly = 0) { provider.fetchWeatherData(any(), any()) }
    }

    @Test
    fun `12-minute-old data is stale for OpenWeatherMap and triggers a fetch`() = runTest {
        setupProviderSelection(WeatherProviderType.OPENWEATHERMAP)
        setupCachedWeather(ageMinutes = 12)

        runCatching { repository.ensureFreshWeatherData(location, forecastDays = 7) }

        coVerify(atLeast = 1) { provider.fetchWeatherData(any(), any()) }
    }

    @Test
    fun `data older than the provider TTL triggers a fetch`() = runTest {
        setupProviderSelection(WeatherProviderType.OPEN_METEO)
        setupCachedWeather(ageMinutes = 16)

        runCatching { repository.ensureFreshWeatherData(location, forecastDays = 7) }

        coVerify(atLeast = 1) { provider.fetchWeatherData(any(), any()) }
    }

    private fun sampleCurrentWeather(lastUpdated: LocalDateTime) = CurrentWeather(
        temperature = 15.0,
        feelsLikeTemperature = 15.0,
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
        visibility = 10_000.0,
        uvIndex = 5.0,
        cloudCover = 30,
        lastUpdated = lastUpdated,
    )

    private fun hourlyForecastsFrom(start: LocalDateTime, count: Int): List<HourlyForecast> {
        val firstHour = start.withMinute(0).withSecond(0).withNano(0)
        return (0 until count).map { offset ->
            HourlyForecast(
                dateTime = firstHour.plusHours(offset.toLong()),
                temperature = 15.0,
                feelsLike = 15.0,
                humidity = 60,
                dewPoint = 9.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 5.0,
            )
        }
    }

    private fun dailyForecastsFrom(start: LocalDate, count: Int): List<DailyForecast> =
        (0 until count).map { offset ->
            DailyForecast(
                date = start.plusDays(offset.toLong()),
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                temperatureMax = 20.0,
                temperatureMin = 11.0,
                feelsLikeMax = 20.0,
                feelsLikeMin = 11.0,
                sunrise = LocalTime.of(6, 0),
                sunset = LocalTime.of(19, 0),
                daylightDurationSeconds = 36_000.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 10.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 5.0,
                averageHumidity = 60,
                averagePressure = 1012.0,
            )
        }
}
