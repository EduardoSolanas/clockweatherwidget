package com.clockweather.app.worker

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import com.clockweather.app.presentation.settings.SettingsViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs: Preferences
        val locations: List<Location>
        try {
            prefs = dataStore.data.first()
            locations = locationRepository.getSavedLocations().first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read prefs or locations, scheduling retry", e)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val forecastDays = prefs[SettingsViewModel.KEY_FORECAST_DAYS] ?: 7
        var anyFailure = false
        locations.forEach { location ->
            try {
                weatherRepository.ensureFreshWeatherData(location, forecastDays)
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed for ${location.id}", e)
                anyFailure = true
            }
        }
        // Always redraw widgets from cache, even on partial failure.
        val app = applicationContext as? ClockWeatherApplication
        app?.refreshAllWidgets(applicationContext)

        return if (!anyFailure) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "WeatherUpdateWorker"
        const val WORK_NAME = "weather_update_work"
    }
}
