package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.WeatherDataProvider
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.provider.RefreshScope
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WeatherProviderType
import com.clockweather.app.domain.model.AIR_QUALITY_MAX_AGE_MINUTES
import com.clockweather.app.domain.model.POLLEN_MAX_AGE_MINUTES
import com.clockweather.app.domain.model.isOptionalSectionFresh
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
        scope: RefreshScope?,
    ) {
        refreshMutex.withLock {
            val cached = getWeatherData(location).first()
            // The saved location row may advance on every small fix. Compare the requested
            // position with the coordinates recorded by the last successful weather fetch so
            // accumulated movement cannot keep reusing an otherwise fresh snapshot.
            val cachedEntity = currentWeatherDao.getCurrentWeather(location.id).first()
            val cacheStillDescribesLocation = WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = cachedEntity?.latitude,
                snapshotLongitude = cachedEntity?.longitude,
                requested = location,
            )
            val referenceDateTime = cached?.locationReferenceDateTime() ?: java.time.LocalDateTime.now()
            val providerType = WeatherProviderPreferences.resolve(
                dataStore.data.first()[WeatherProviderPreferences.KEY_WEATHER_PROVIDER]
            )
            val effectiveMaxAgeMinutes = maxAgeMinutes ?: providerType.currentMaxAgeMinutes
            val effectiveScope = scope ?: backgroundScope()
            // Background work never fetches hourly, so requiring it would leave the widgets
            // permanently stale and re-enqueueing. The app is the only caller that needs it.
            val isFresh = cacheStillDescribesLocation && isWeatherDataFresh(
                cached,
                referenceDateTime,
                forecastDays,
                effectiveMaxAgeMinutes,
                requireHourly = effectiveScope.includeHourly,
            )
            if (isFresh && optionalSectionsFresh(location, referenceDateTime, effectiveScope)) return

            refreshAndPersist(location, forecastDays, effectiveScope)
        }
    }

    override suspend fun forceRefreshWeatherData(
        location: Location,
        forecastDays: Int,
        scope: RefreshScope?,
    ) {
        refreshMutex.withLock {
            refreshAndPersist(location, forecastDays, scope ?: backgroundScope())
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

    /**
     * Whether the optional sections this caller asked for are still recent enough to skip.
     *
     * Core weather and forecast coverage say nothing about air quality or pollen: they carry
     * their own cadences and their own timestamps. A warm resume after background work — which
     * buys neither — would otherwise sit behind fresh current weather with an expired air
     * quality reading, or none at all, and never ask for more.
     *
     * Sections the scope does not request are not consulted, so background work stays as cheap
     * as it was and cannot be held stale by data no widget displays.
     */
    private suspend fun optionalSectionsFresh(
        location: Location,
        referenceDateTime: java.time.LocalDateTime,
        scope: RefreshScope,
    ): Boolean {
        if (!scope.includeAirQuality && !scope.includePollen) return true
        val entity = currentWeatherDao.getCurrentWeather(location.id).first() ?: return false
        val airQualityFresh = !scope.includeAirQuality || isOptionalSectionFresh(
            parseTimestamp(entity.aqLastUpdated),
            referenceDateTime,
            AIR_QUALITY_MAX_AGE_MINUTES,
        )
        val pollenFresh = !scope.includePollen || isOptionalSectionFresh(
            parseTimestamp(entity.pollenLastUpdated),
            referenceDateTime,
            POLLEN_MAX_AGE_MINUTES,
        )
        return airQualityFresh && pollenFresh
    }

    private fun parseTimestamp(value: String?): java.time.LocalDateTime? = value?.let {
        runCatching {
            java.time.LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }.getOrNull()
    }

    /**
     * The widget's pollen bar is the only home-screen use of an optional section, so it decides
     * whether background work still pays for pollen.
     */
    private suspend fun backgroundScope(): RefreshScope = RefreshScope.background(
        pollenShownInWidget = dataStore.data.first()[SettingsViewModel.KEY_SHOW_POLLEN_IN_WIDGET] ?: true
    )

    private suspend fun refreshAndPersist(
        location: Location,
        forecastDays: Int,
        scope: RefreshScope,
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
                scope = scope
            )
        }
        persistWeatherData(weatherData.normalizeDailyConditions(), location.id, scope, cachedEntity)
    }

    private suspend fun persistWeatherData(
        data: WeatherData,
        locationId: Long,
        scope: RefreshScope,
        previous: CurrentWeatherEntity?,
    ) {
        val answeredAt = java.time.LocalDateTime.now()
        // A section that was asked for and came back empty, or only echoed the stale value we
        // supplied as cache, has been answered: it is unavailable here, not merely unseen.
        // Recording when we asked keeps that answer for one TTL. A section nobody asked for
        // keeps whatever timestamp it had, so an unrelated refresh cannot make it look fresh.
        val previousAirQualityAt = parseTimestamp(previous?.aqLastUpdated)
        val returnedAirQualityAt = data.airQuality?.lastUpdated
        val airQualityAnsweredAt = if (
            scope.includeAirQuality && returnedAirQualityAt == previousAirQualityAt
        ) {
            answeredAt
        } else {
            returnedAirQualityAt ?: previousAirQualityAt
        }
        val previousPollenAt = parseTimestamp(previous?.pollenLastUpdated)
        val returnedPollenAt = data.pollenLastUpdated
        val pollenAnsweredAt = if (
            scope.includePollen && returnedPollenAt == previousPollenAt
        ) {
            answeredAt
        } else {
            returnedPollenAt ?: previousPollenAt
        }

        // The location row keeps its id across a move, so it cannot tell us who these hours
        // belong to. The coordinates the previous fetch recorded on the weather row can.
        val previousLatitude = previous?.latitude
        val previousLongitude = previous?.longitude
        val movedAway = previousLatitude != null && previousLongitude != null &&
            WeatherRefreshLocationResolver.hasMovedSignificantly(
                previousLatitude,
                previousLongitude,
                data.location.latitude,
                data.location.longitude,
            )

        database.withTransaction {
            currentWeatherDao.insertCurrentWeather(
                entityMapper.mapCurrentWeatherToEntity(
                    domain = data.currentWeather,
                    locationId = locationId,
                    airQuality = data.airQuality,
                    locationName = data.location.name,
                    latitude = data.location.latitude,
                    longitude = data.location.longitude,
                    pollenLastUpdated = pollenAnsweredAt,
                    airQualityLastUpdated = airQualityAnsweredAt
                )
            )
            // Hours are replaced wholesale or not touched at all, never merged, so the cache
            // always holds one fetch on one clock. A refresh that did not buy them leaves the
            // previous set in place for the app to show until it fetches its own.
            if (scope.includeHourly) {
                hourlyForecastDao.deleteHourlyForecasts(locationId)
                hourlyForecastDao.insertHourlyForecasts(
                    data.hourlyForecasts.map { entityMapper.mapHourlyToEntity(it, locationId) }
                )
            } else if (movedAway) {
                // This refresh bought no hours, so there is nothing to put in their place — but
                // the ones on file describe the city the user left. An empty graph is a gap the
                // app can fill on its next foreground fetch; the alternative is the previous
                // city's hours under this city's name.
                hourlyForecastDao.deleteHourlyForecasts(locationId)
            }
            dailyForecastDao.deleteDailyForecasts(locationId)
            dailyForecastDao.insertDailyForecasts(
                data.dailyForecasts.map { entityMapper.mapDailyToEntity(it, locationId) }
            )
        }
    }
}
