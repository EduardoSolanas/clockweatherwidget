package com.clockweather.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clockweather.app.data.local.entity.HourlyForecastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlyForecastDao {

    @Query("SELECT * FROM hourly_forecast WHERE locationId = :locationId ORDER BY dateTime ASC")
    fun getHourlyForecasts(locationId: Long): Flow<List<HourlyForecastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyForecasts(entities: List<HourlyForecastEntity>)

    @Query("DELETE FROM hourly_forecast WHERE locationId = :locationId")
    suspend fun deleteHourlyForecasts(locationId: Long)
}

