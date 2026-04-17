package com.clockweather.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.clockweather.app.R
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.remote.api.NominatimReverseGeocodingApi
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val reverseGeocodingApi: NominatimReverseGeocodingApi,
    private val entityMapper: WeatherEntityMapper,
    private val dtoMapper: WeatherDtoMapper
) : LocationRepository {

    companion object {
        private const val GEO_DEBUG_TAG = "CW_GeoDebug"
    }

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
            Log.d(GEO_DEBUG_TAG, "getCurrentLocation() invoked")

            val lastKnown = fusedLocationClient.lastLocation.await()
            if (lastKnown != null && (System.currentTimeMillis() - lastKnown.time) < 15 * 60 * 1000) {
                Log.d(
                    GEO_DEBUG_TAG,
                    "lastLocation=lat=${lastKnown.latitude}, lon=${lastKnown.longitude}, ageMs=${System.currentTimeMillis() - lastKnown.time}, accuracy=${lastKnown.accuracy}, provider=${lastKnown.provider}"
                )
                Log.d(GEO_DEBUG_TAG, "Using recent lastLocation")
                return mapToLocation(lastKnown)
            }

            val cancellationToken = CancellationTokenSource()
            var androidLocation = withTimeoutOrNull(10_000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).await()
            }

            Log.d(
                GEO_DEBUG_TAG,
                "balancedPower result=${androidLocation?.let { "lat=${it.latitude}, lon=${it.longitude}, accuracy=${it.accuracy}, provider=${it.provider}" } ?: "null"}"
            )

            if (androidLocation == null) {
                androidLocation = withTimeoutOrNull(5_000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                }

                Log.d(
                    GEO_DEBUG_TAG,
                    "highAccuracy result=${androidLocation?.let { "lat=${it.latitude}, lon=${it.longitude}, accuracy=${it.accuracy}, provider=${it.provider}" } ?: "null"}"
                )
            }

            if (androidLocation != null) {
                mapToLocation(androidLocation)
            } else {
                Log.d(GEO_DEBUG_TAG, "No Android location fix; using saved location or fallback")
                locationDao.getCurrentLocation()?.let { entityMapper.mapLocationToDomain(it) }
                    ?: getFallbackLocation()
            }
        } catch (e: Exception) {
            Log.w(GEO_DEBUG_TAG, "getCurrentLocation() failed; using saved location or fallback", e)
            locationDao.getCurrentLocation()?.let { entityMapper.mapLocationToDomain(it) }
                ?: getFallbackLocation()
        }
    }

    private suspend fun mapToLocation(androidLocation: android.location.Location): Location {
        Log.d(
            GEO_DEBUG_TAG,
            "mapToLocation lat=${androidLocation.latitude}, lon=${androidLocation.longitude}, provider=${androidLocation.provider}, accuracy=${androidLocation.accuracy}"
        )

        val androidReverse = reverseGeocode(androidLocation.latitude, androidLocation.longitude)
        if (androidReverse?.isSpecificName == true) {
            Log.d(GEO_DEBUG_TAG, "Using Android Geocoder result name='${androidReverse.name}', country='${androidReverse.country}'")
            return androidReverse.toLocation(androidLocation)
        }

        if (androidReverse != null) {
            Log.d(GEO_DEBUG_TAG, "Android Geocoder result deemed broad: '${androidReverse.name}' -> trying reverse geocode fallback")
        }

        reverseGeocodeOnline(androidLocation.latitude, androidLocation.longitude)?.let { reverse ->
            Log.d(GEO_DEBUG_TAG, "Using reverse geocode fallback name='${reverse.name}', country='${reverse.country}'")
            return reverse.toLocation(androidLocation)
        }

        androidReverse?.let {
            Log.d(GEO_DEBUG_TAG, "Falling back to broad Android Geocoder result='${it.name}'")
            return it.toLocation(androidLocation)
        }

        return Location(
            name = context.getString(R.string.label_current_location),
            country = "",
            latitude = androidLocation.latitude,
            longitude = androidLocation.longitude,
            isCurrentLocation = true
        )
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? {
        return runCatching {
            if (!Geocoder.isPresent()) return null

            val geocoder = Geocoder(context, Locale.getDefault())
            val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() ?: return null
            val fallbackLabel = context.getString(R.string.label_current_location)
            val resolvedName = resolveCurrentLocationNameDetails(
                locality = address.locality,
                subLocality = address.subLocality,
                subAdminArea = address.subAdminArea,
                adminArea = address.adminArea,
                fallbackLabel = fallbackLabel
            )
            val country = address.countryCode?.takeIf { it.isNotBlank() }
                ?: address.countryName.orEmpty()

            Log.d(
                GEO_DEBUG_TAG,
                "Android Geocoder raw lat=$latitude, lon=$longitude, feature='${address.featureName}', locality='${address.locality}', subLocality='${address.subLocality}', subAdmin='${address.subAdminArea}', admin='${address.adminArea}', thoroughfare='${address.thoroughfare}', subThoroughfare='${address.subThoroughfare}', postalCode='${address.postalCode}', countryCode='${address.countryCode}', countryName='${address.countryName}', addressLine0='${address.getAddressLine(0)}', resolved='${resolvedName}'"
            )

            ReverseGeocodeResult(
                name = resolvedName.value,
                country = country,
                isSpecificName = resolvedName.isSpecific,
                area = resolvedName.area
            )
        }.getOrElse { error ->
            Log.w(GEO_DEBUG_TAG, "Android Geocoder reverse lookup failed", error)
            null
        }
    }

    private suspend fun reverseGeocodeOnline(latitude: Double, longitude: Double): ReverseGeocodeResult? {
        return runCatching {
            val response = reverseGeocodingApi.reverseGeocode(latitude = latitude, longitude = longitude)
            val address = response.address ?: return null
            val fallbackLabel = context.getString(R.string.label_current_location)
            val resolvedName = resolveCurrentLocationNameDetails(
                locality = null,
                cityDistrict = address.cityDistrict,
                suburb = address.suburb,
                neighbourhood = address.neighbourhood,
                subAdminArea = address.city ?: address.town ?: address.village ?: address.county,
                adminArea = address.state,
                fallbackLabel = response.displayName?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
                    ?: fallbackLabel
            )
            val country = address.countryCode?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
                ?: address.country.orEmpty()

            Log.d(
                GEO_DEBUG_TAG,
                "Reverse geocode fallback lat=$latitude, lon=$longitude, cityDistrict='${address.cityDistrict}', suburb='${address.suburb}', neighbourhood='${address.neighbourhood}', city='${address.city}', county='${address.county}', state='${address.state}', resolved='${resolvedName}'"
            )

            ReverseGeocodeResult(
                name = resolvedName.value,
                country = country,
                isSpecificName = resolvedName.isSpecific,
                area = resolvedName.area
            )
        }.getOrElse { error ->
            Log.w(GEO_DEBUG_TAG, "Reverse geocode fallback failed", error)
            null
        }
    }

    override fun getFallbackLocation(): Location {
        return Location(
            id = 0,
            name = context.getString(R.string.label_default_location),
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true
        ).also {
            Log.d(GEO_DEBUG_TAG, "Falling back to generic label='${it.name}'")
        }
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

    private fun ReverseGeocodeResult.toLocation(androidLocation: android.location.Location): Location {
        return Location(
            name = name,
            country = country,
            latitude = androidLocation.latitude,
            longitude = androidLocation.longitude,
            isCurrentLocation = true,
            area = area
        )
    }

    private data class ReverseGeocodeResult(
        val name: String,
        val country: String,
        val isSpecificName: Boolean,
        val area: String? = null
    )
}
