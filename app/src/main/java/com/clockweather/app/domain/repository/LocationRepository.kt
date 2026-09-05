package com.clockweather.app.domain.repository

import com.clockweather.app.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getSavedLocations(): Flow<List<Location>>
    suspend fun saveLocation(location: Location): Long
    suspend fun deleteLocation(locationId: Long)
    suspend fun getCurrentLocation(): Location?
    /** Resolve supplied coordinates to a displayable current-location row. */
    suspend fun resolveLocation(latitude: Double, longitude: Double): Location
    suspend fun searchLocations(query: String): List<Location>
    fun getLocationById(id: Long): Flow<Location?>
    fun getFallbackLocation(): Location
}
