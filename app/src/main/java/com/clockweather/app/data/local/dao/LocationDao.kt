package com.clockweather.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.clockweather.app.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM locations ORDER BY id ASC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun getLocationById(id: Long): Flow<LocationEntity?>

    @Query("SELECT * FROM locations WHERE isCurrentLocation = 1 LIMIT 1")
    suspend fun getCurrentLocation(): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(entity: LocationEntity): Long

    @Update
    suspend fun updateLocation(entity: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocation(id: Long)

    @Query("UPDATE locations SET isCurrentLocation = 0")
    suspend fun clearCurrentLocation()
}

