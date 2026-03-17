package com.clockweather.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val locations = locationRepository.getSavedLocations().first()
            locations.forEach { location ->
                weatherRepository.refreshWeatherData(location)
            }
            // Notify widgets to update via centralized helper
            val app = applicationContext as? ClockWeatherApplication
            app?.refreshAllWidgets(applicationContext, isClockTick = false)
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "weather_update_work"
    }
}
