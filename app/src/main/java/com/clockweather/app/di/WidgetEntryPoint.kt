package com.clockweather.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun locationRepository(): LocationRepository
    fun dataStore(): DataStore<Preferences>
}

