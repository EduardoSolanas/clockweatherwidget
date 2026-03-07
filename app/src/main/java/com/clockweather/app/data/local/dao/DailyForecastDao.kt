package com.clockweather.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clockweather.app.data.local.entity.DailyForecastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyForecastDao {

    @Query("SELECT * FROM daily_forecast WHERE locationId = :locationId ORDER BY date ASC")
    fun getDailyForecasts(locationId: Long): Flow<List<DailyForecastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyForecasts(entities: List<DailyForecastEntity>)

    @Query("DELETE FROM daily_forecast WHERE locationId = :locationId")
    suspend fun deleteDailyForecasts(locationId: Long)
}

