package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.clockweather.app.presentation.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object WeatherUpdateScheduler {

    /** WorkManager enforces a minimum repeat interval of 15 minutes. */
    private const val MIN_INTERVAL_MINUTES = 15L
    private const val IMMEDIATE_WORK_NAME = "weather_update_immediate_work"

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
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            clampedInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // UPDATE: adjusts interval on existing work without cancelling any in-progress run
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeatherUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    suspend fun ensureScheduled(context: Context, dataStore: DataStore<Preferences>) {
        val intervalMinutes = dataStore.data.first()[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL]
            ?: SettingsViewModel.DEFAULT_WEATHER_REFRESH_INTERVAL_MINUTES
        schedule(context, intervalMinutes)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherUpdateWorker.WORK_NAME)
    }

    /**
     * Enqueue a one-time freshness-gated refresh (e.g. on widget add or boot).
     * Uses [ensureFreshWeatherData] internally, so no network call is made when cached data
     * is still within [com.clockweather.app.domain.model.CURRENT_MAX_AGE_MINUTES].
     */
    fun scheduleImmediateRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
