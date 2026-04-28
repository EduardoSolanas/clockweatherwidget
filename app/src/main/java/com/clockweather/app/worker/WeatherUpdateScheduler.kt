package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object WeatherUpdateScheduler {

    /** WorkManager enforces a minimum repeat interval of 15 minutes. */
    private const val MIN_INTERVAL_MINUTES = 15L

    /**
     * Enqueue (or update) the periodic weather-fetch worker.
     *
     * @param intervalMinutes How often to fetch weather. Clamped to [MIN_INTERVAL_MINUTES].
     *                        Defaults to 30 minutes.
     */
    fun schedule(context: Context, intervalMinutes: Int = 30) {
        val clampedInterval = intervalMinutes.toLong().coerceAtLeast(MIN_INTERVAL_MINUTES)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            clampedInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // UPDATE: adjusts interval on existing work without cancelling any in-progress run
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeatherUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    suspend fun ensureScheduled(context: Context, dataStore: DataStore<Preferences>) {
        val intervalMinutes = dataStore.data.first()[intPreferencesKey("update_interval_minutes")] ?: 30
        schedule(context, intervalMinutes)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherUpdateWorker.WORK_NAME)
    }

    fun scheduleImmediateRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

