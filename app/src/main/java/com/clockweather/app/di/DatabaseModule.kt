package com.clockweather.app.di

import android.content.Context
import androidx.room.Room
import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWeatherDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(
            context,
            WeatherDatabase::class.java,
            WeatherDatabase.DATABASE_NAME
        )
        .addMigrations(WeatherDatabase.MIGRATION_1_2, WeatherDatabase.MIGRATION_2_3)
        .build()

    @Provides
    fun provideCurrentWeatherDao(db: WeatherDatabase): CurrentWeatherDao =
        db.currentWeatherDao()

    @Provides
    fun provideHourlyForecastDao(db: WeatherDatabase): HourlyForecastDao =
        db.hourlyForecastDao()

    @Provides
    fun provideDailyForecastDao(db: WeatherDatabase): DailyForecastDao =
        db.dailyForecastDao()

    @Provides
    fun provideLocationDao(db: WeatherDatabase): LocationDao =
        db.locationDao()
}

