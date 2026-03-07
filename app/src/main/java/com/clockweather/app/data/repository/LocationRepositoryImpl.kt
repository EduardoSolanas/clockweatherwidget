package com.clockweather.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.remote.api.OpenMeteoGeocodingApi
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val entityMapper: WeatherEntityMapper,
    private val dtoMapper: WeatherDtoMapper
) : LocationRepository {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override fun getSavedLocations(): Flow<List<Location>> =
        locationDao.getAllLocations().map { entities ->
            entities.map { entityMapper.mapLocationToDomain(it) }
        }

    override suspend fun saveLocation(location: Location): Long {
        val entity = entityMapper.mapLocationToEntity(location)
        return locationDao.insertLocation(entity)
    }

    override suspend fun deleteLocation(locationId: Long) {
        locationDao.deleteLocation(locationId)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Location? {
        return try {
            val cancellationToken = CancellationTokenSource()

            // 5 second timeout — emulators often have no GPS signal
            val androidLocation = withTimeoutOrNull(5_000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).await()
            }

            if (androidLocation != null) {
                // Got real GPS — try to reverse geocode
                try {
                    val results = geocodingApi.searchLocations(
                        name = "${androidLocation.latitude},${androidLocation.longitude}",
                        count = 1
                    )
                    results.results?.firstOrNull()?.let { geoDto ->
                        dtoMapper.mapGeoLocation(geoDto).copy(
                            latitude = androidLocation.latitude,
                            longitude = androidLocation.longitude,
                            isCurrentLocation = true
                        )
                    }
                } catch (e: Exception) {
                    null
                } ?: Location(
                    name = "Current Location",
                    country = "",
                    latitude = androidLocation.latitude,
                    longitude = androidLocation.longitude,
                    isCurrentLocation = true
                )
            } else {
                // GPS timed out (emulator) — try last saved, then fall back to London
                locationDao.getCurrentLocation()?.let { entityMapper.mapLocationToDomain(it) }
                    ?: FALLBACK_LOCATION
            }
        } catch (e: Exception) {
            locationDao.getCurrentLocation()?.let { entityMapper.mapLocationToDomain(it) }
                ?: FALLBACK_LOCATION
        }
    }

    companion object {
        /** Default location used when GPS is unavailable (e.g. emulator with no signal) */
        val FALLBACK_LOCATION = Location(
            id = 0,
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true
        )
    }

    override suspend fun searchLocations(query: String): List<Location> {
        return try {
            val response = geocodingApi.searchLocations(name = query)
            response.results?.map { dtoMapper.mapGeoLocation(it) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getLocationById(id: Long): Flow<Location?> =
        locationDao.getLocationById(id).map { entity ->
            entity?.let { entityMapper.mapLocationToDomain(it) }
        }
}

