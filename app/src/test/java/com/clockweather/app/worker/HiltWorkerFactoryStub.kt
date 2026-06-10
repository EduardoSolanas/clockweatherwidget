package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository

/**
 * A [WorkerFactory] that injects test doubles into [WeatherUpdateWorker],
 * bypassing Hilt's code-generated factory for unit tests.
 */
class HiltWorkerFactoryStub(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val dataStore: DataStore<Preferences>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ) = WeatherUpdateWorker(appContext, workerParameters, weatherRepository, locationRepository, dataStore)
}
