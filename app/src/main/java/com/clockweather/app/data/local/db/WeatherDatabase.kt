package com.clockweather.app.data.local.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.entity.CurrentWeatherEntity
import com.clockweather.app.data.local.entity.DailyForecastEntity
import com.clockweather.app.data.local.entity.HourlyForecastEntity
import com.clockweather.app.data.local.entity.LocationEntity

@Database(
    entities = [
        CurrentWeatherEntity::class,
        HourlyForecastEntity::class,
        DailyForecastEntity::class,
        LocationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun currentWeatherDao(): CurrentWeatherDao
    abstract fun hourlyForecastDao(): HourlyForecastDao
    abstract fun dailyForecastDao(): DailyForecastDao
    abstract fun locationDao(): LocationDao

    companion object {
        const val DATABASE_NAME = "weather_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqCo REAL")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqNo2 REAL")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqO3 REAL")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqSo2 REAL")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqPm25 REAL")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqPm10 REAL")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqUsEpaIndex INTEGER")
                db.execSQL("ALTER TABLE current_weather ADD COLUMN aqGbDefraIndex INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE locations ADD COLUMN area TEXT")
            }
        }
    }
}

