package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.WeatherDataProvider
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.provider.HourlyScope
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.hasExtendedHourlyCoverage
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WeatherProviderType
import com.clockweather.app.domain.model.isWeatherDataFresh
import com.clockweather.app.domain.model.locationReferenceDateTime
import com.clockweather.app.domain.model.normalizeDailyConditions
import com.clockweather.app.domain.repository.WeatherRepository
import androidx.room.withTransaction
import com.clockweather.app.worker.WeatherRefreshLocationResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val providerFactory: WeatherDataProviderFactory,
    private val database: WeatherDatabase,
    private val currentWeatherDao: CurrentWeatherDao,
    private val hourlyForecastDao: HourlyForecastDao,
    private val dailyForecastDao: DailyForecastDao,
    private val locationDao: LocationDao,
    private val entityMapper: WeatherEntityMapper
) : WeatherRepository {

    // B4: prevents concurrent widgets from firing duplicate network requests for the same location
    private val refreshMutex = Mutex()

    override fun getWeatherData(location: Location): Flow<WeatherData?> {
        return combine(
            currentWeatherDao.getCurrentWeather(location.id),
            hourlyForecastDao.getHourlyForecasts(location.id),
            dailyForecastDao.getDailyForecasts(location.id),
            locationDao.getLocationById(location.id)
        ) { current, hourly, daily, locationEntity ->
            current ?: return@combine null
            val latestLocation = locationEntity?.let { entityMapper.mapLocationToDomain(it) } ?: location
            val snapshotLocation = if (!current.locationName.isNullOrBlank()) {
                latestLocation.copy(
                    name = current.locationName,
                    latitude = current.latitude ?: latestLocation.latitude,
                    longitude = current.longitude ?: latestLocation.longitude
                )
            } else {
                latestLocation
            }
            val pollenUpdated = current.pollenLastUpdated?.let {
                runCatching { java.time.LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
            }
            WeatherData(
                location = snapshotLocation,
                currentWeather = entityMapper.mapCurrentWeatherToDomain(current),
                hourlyForecasts = hourly.map { entityMapper.mapHourlyToDomain(it) },
                dailyForecasts = daily.map { entityMapper.mapDailyToDomain(it) },
                airQuality = entityMapper.mapAirQualityFromEntity(current),
                pollenLastUpdated = pollenUpdated
            )
        }
    }

    override suspend fun ensureFreshWeatherData(
        location: Location,
        forecastDays: Int,
        maxAgeMinutes: Long?,
        hourlyScope: HourlyScope,
    ) {
        refreshMutex.withLock {
            val cached = getWeatherData(location).first()
            val referenceDateTime = cached?.locationReferenceDateTime() ?: java.time.LocalDateTime.now()
            val providerType = WeatherProviderPreferences.resolve(
                dataStore.data.first()[WeatherProviderPreferences.KEY_WEATHER_PROVIDER]
            )
            val effectiveMaxAgeMinutes = maxAgeMinutes ?: providerType.currentMaxAgeMinutes
            val isFresh = isWeatherDataFresh(cached, referenceDateTime, forecastDays, effectiveMaxAgeMinutes)
            // Fresh core weather is not enough for a caller that needs the later days: routine
            // refreshes only keep a rolling 24 hours, so the rest has to be fetched on demand.
            val needsExtendedHours = hourlyScope == HourlyScope.EXTENDED &&
                !hasExtendedHourlyCoverage(cached?.hourlyForecasts.orEmpty(), referenceDateTime)
            if (isFresh && !needsExtendedHours) return

            refreshAndPersist(location, forecastDays, hourlyScope)
        }
    }

    override suspend fun forceRefreshWeatherData(
        location: Location,
        forecastDays: Int,
        hourlyScope: HourlyScope,
    ) {
        refreshMutex.withLock {
            refreshAndPersist(location, forecastDays, hourlyScope)
        }
    }

    /**
     * Tries the selected provider first. If it fails and a different default
     * provider is configured, falls back to that one — all through the
     * [WeatherDataProvider] interface, no hardcoded provider types.
     */
    private suspend fun fetchWithFallback(
        providerType: WeatherProviderType,
        fetch: suspend (WeatherDataProvider, WeatherProviderType) -> WeatherData
    ): WeatherData {
        return try {
            fetch(providerFactory.get(providerType), providerType)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val fallbackType = WeatherProviderPreferences.defaultProvider()
            if (fallbackType == providerType) throw error
            fetch(providerFactory.get(fallbackType), fallbackType)
        }
    }

    private suspend fun refreshAndPersist(
        location: Location,
        forecastDays: Int,
        hourlyScope: HourlyScope = HourlyScope.NEAR_TERM,
    ) {
        // Optional sections are reused from this cache, so it may only be offered when it was
        // actually recorded at the requested position. The weather row's own coordinates are
        // the ones that matter: the location row has already moved by this point, and rows
        // migrated from before those columns existed cannot prove where they came from.
        val cachedEntity = currentWeatherDao.getCurrentWeather(location.id).first()
        val cached = getWeatherData(location).first()?.takeIf {
            WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = cachedEntity?.latitude,
                snapshotLongitude = cachedEntity?.longitude,
                requested = location,
            )
        }
        val providerType = WeatherProviderPreferences.resolve(
            dataStore.data.first()[WeatherProviderPreferences.KEY_WEATHER_PROVIDER]
        )
        val weatherData = fetchWithFallback(providerType) { provider, actualProviderType ->
            provider.fetchWeatherData(
                location = location,
                forecastDays = forecastDays.coerceIn(1, actualProviderType.maxForecastDays),
                cachedData = cached,
                hourlyScope = hourlyScope
            )
        }
        persistWeatherData(weatherData.normalizeDailyConditions(), location.id)
    }

    private suspend fun persistWeatherData(data: WeatherData, locationId: Long) {
        database.withTransaction {
            currentWeatherDao.insertCurrentWeather(
                entityMapper.mapCurrentWeatherToEntity(
                    domain = data.currentWeather,
                    locationId = locationId,
                    airQuality = data.airQuality,
                    locationName = data.location.name,
                    latitude = data.location.latitude,
                    longitude = data.location.longitude,
                    pollenLastUpdated = data.pollenLastUpdated
                )
            )
            hourlyForecastDao.deleteHourlyForecasts(locationId)
            hourlyForecastDao.insertHourlyForecasts(
                data.hourlyForecasts.map { entityMapper.mapHourlyToEntity(it, locationId) }
            )
            dailyForecastDao.deleteDailyForecasts(locationId)
            dailyForecastDao.insertDailyForecasts(
                data.dailyForecasts.map { entityMapper.mapDailyToEntity(it, locationId) }
            )
        }
    }
}
