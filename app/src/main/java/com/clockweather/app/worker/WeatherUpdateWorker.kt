package com.clockweather.app.worker

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import com.clockweather.app.presentation.settings.SettingsViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.hypot

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
        val refreshIntervalMinutes = SettingsViewModel.normalizeWeatherRefreshInterval(
            prefs[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL]
        )
        val refreshMode = weatherRefreshMode(inputData)
        var anyFailure = false
        locations.forEach { savedLocation ->
            try {
                val refreshLocation = if (savedLocation.isCurrentLocation) {
                    WeatherRefreshLocationResolver.resolve(
                        savedLocation,
                        locationRepository.getCurrentLocation(),
                    ).also { refreshedLocation ->
                        if (refreshedLocation != savedLocation) {
                            locationRepository.saveLocation(refreshedLocation)
                        }
                    }
                } else {
                    savedLocation
                }
                // Cached weather is keyed by the location row, so right after a move it
                // still holds the city the user left. Refetch however recent it looks.
                val effectiveMode = if (
                    WeatherRefreshLocationResolver.hasMovedSignificantly(savedLocation, refreshLocation)
                ) {
                    WeatherRefreshMode.FORCE
                } else {
                    refreshMode
                }
                when (effectiveMode) {
                    WeatherRefreshMode.FORCE ->
                        weatherRepository.forceRefreshWeatherData(refreshLocation, forecastDays)
                    WeatherRefreshMode.ENSURE_FRESH ->
                        weatherRepository.ensureFreshWeatherData(
                            refreshLocation,
                            forecastDays,
                            maxAgeMinutes = refreshIntervalMinutes.toLong(),
                        )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed for ${savedLocation.id}", e)
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
        const val INPUT_FORCE_REFRESH = "force_refresh"
    }
}

internal enum class WeatherRefreshMode { ENSURE_FRESH, FORCE }

internal fun weatherRefreshMode(inputData: Data): WeatherRefreshMode =
    if (inputData.getBoolean(WeatherUpdateWorker.INPUT_FORCE_REFRESH, false)) {
        WeatherRefreshMode.FORCE
    } else {
        WeatherRefreshMode.ENSURE_FRESH
    }

internal object WeatherRefreshLocationResolver {

    /**
     * Weather is modelled on grids several kilometres wide and every fix jitters a
     * little, so only a move of at least this distance puts the user somewhere the
     * previously cached weather no longer describes.
     */
    private const val SIGNIFICANT_MOVE_METERS = 5_000.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun resolve(savedLocation: Location, detectedLocation: Location?): Location {
        if (!savedLocation.isCurrentLocation || detectedLocation == null) return savedLocation

        return detectedLocation.copy(
            id = savedLocation.id,
            isCurrentLocation = true,
        )
    }

    /** True when weather cached for [from] no longer describes [to]. */
    fun hasMovedSignificantly(from: Location, to: Location): Boolean =
        distanceMeters(from, to) >= SIGNIFICANT_MOVE_METERS

    /** Equirectangular approximation — far below the threshold's precision needs. */
    private fun distanceMeters(from: Location, to: Location): Double {
        val meanLatitude = Math.toRadians((from.latitude + to.latitude) / 2)
        val northSouth = Math.toRadians(to.latitude - from.latitude)
        val eastWest = Math.toRadians(to.longitude - from.longitude) * cos(meanLatitude)
        return EARTH_RADIUS_METERS * hypot(northSouth, eastWest)
    }
}
