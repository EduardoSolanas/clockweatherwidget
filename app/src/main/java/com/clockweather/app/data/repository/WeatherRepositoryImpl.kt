package com.clockweather.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.provider.WeatherDataProviderFactory
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.repository.WeatherRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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

    override fun getWeatherData(location: Location): Flow<WeatherData> {
        return combine(
            currentWeatherDao.getCurrentWeather(location.id),
            hourlyForecastDao.getHourlyForecasts(location.id),
            dailyForecastDao.getDailyForecasts(location.id),
            locationDao.getLocationById(location.id)
        ) { current, hourly, daily, locationEntity ->
            current ?: return@combine null
            val latestLocation = locationEntity?.let { entityMapper.mapLocationToDomain(it) } ?: location
            WeatherData(
                location = latestLocation,
                currentWeather = entityMapper.mapCurrentWeatherToDomain(current),
                hourlyForecasts = hourly.map { entityMapper.mapHourlyToDomain(it) },
                dailyForecasts = daily.map { entityMapper.mapDailyToDomain(it) },
                airQuality = entityMapper.mapAirQualityFromEntity(current)
            )
        }.filterNotNull()
    }

    override suspend fun refreshWeatherData(location: Location, forecastDays: Int) {
        refreshMutex.withLock {
            val providerType = WeatherProviderPreferences.resolve(
                dataStore.data.first()[WeatherProviderPreferences.KEY_WEATHER_PROVIDER]
            )
            val provider = providerFactory.get(providerType)
            val weatherData = provider.fetchWeatherData(
                location = location,
                forecastDays = forecastDays.coerceIn(1, providerType.maxForecastDays)
            )
            persistWeatherData(weatherData, location.id)
        }
    }

    override fun getCachedWeatherData(locationId: Long): Flow<WeatherData?> {
        return combine(
            currentWeatherDao.getCurrentWeather(locationId),
            hourlyForecastDao.getHourlyForecasts(locationId),
            dailyForecastDao.getDailyForecasts(locationId),
            locationDao.getLocationById(locationId)
        ) { current, hourly, daily, locationEntity ->
            val loc = locationEntity?.let { entityMapper.mapLocationToDomain(it) } ?: return@combine null
            current ?: return@combine null
            WeatherData(
                location = loc,
                currentWeather = entityMapper.mapCurrentWeatherToDomain(current),
                hourlyForecasts = hourly.map { entityMapper.mapHourlyToDomain(it) },
                dailyForecasts = daily.map { entityMapper.mapDailyToDomain(it) },
                airQuality = entityMapper.mapAirQualityFromEntity(current)
            )
        }
    }

    private suspend fun persistWeatherData(data: WeatherData, locationId: Long) {
        database.withTransaction {
            currentWeatherDao.insertCurrentWeather(
                entityMapper.mapCurrentWeatherToEntity(data.currentWeather, locationId, data.airQuality)
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
