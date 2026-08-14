package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.worker.WeatherRefreshLocationResolver
import com.clockweather.app.worker.WeatherUpdateScheduler
import com.google.android.gms.location.LocationResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext

/**
 * Broadcast receiver for passive location updates delivered by FusedLocationProviderClient.
 *
 * When another app (like Twitter or Maps) requests location, this receiver inspects the fix.
 * If the displacement exceeds the relocation threshold (>= 5 km) from the saved current-location
 * row, it enqueues an expedited weather refresh worker.
 *
 * It deliberately writes nothing itself. Reverse geocoding and weather HTTP calls belong in
 * [com.clockweather.app.worker.WeatherUpdateWorker], both to respect the BroadcastReceiver
 * execution budget and so the city name and coordinates are only ever updated together.
 *
 * @param workContext overridable so tests can run the handler on a deterministic dispatcher.
 */
class LocationUpdatesReceiver(
    private val workContext: CoroutineContext = Dispatchers.IO,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!LocationResult.hasResult(intent)) return
        val result = LocationResult.extractResult(intent) ?: return
        val fix = result.lastLocation ?: return

        Log.d(TAG, "Passive location fix received: lat=${fix.latitude}, lon=${fix.longitude}, accuracy=${fix.accuracy}m")

        val pendingResult = goAsync()

        CoroutineScope(workContext).launch {
            try {
                // goAsync() holds the broadcast open until finish(), so the work has to
                // be bounded well inside the platform's ~10s budget for receivers.
                withTimeout(WORK_BUDGET_MS) {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    )
                    val locationRepo = entryPoint.locationRepository()
                    val locations = locationRepo.getSavedLocations().first()
                    val currentLocation = locations.firstOrNull { it.isCurrentLocation }

                    if (currentLocation != null && WeatherRefreshLocationResolver.hasMovedSignificantly(
                            fromLat = currentLocation.latitude,
                            fromLon = currentLocation.longitude,
                            toLat = fix.latitude,
                            toLon = fix.longitude
                        )
                    ) {
                        Log.i(
                            TAG,
                            "Relocation detected via passive fix (>=5km from ${currentLocation.name}): scheduling refresh"
                        )
                    // Hand the whole update to the worker, which resolves the city name and
                    // the weather for the new coordinates together. Persisting coordinates
                    // here would leave the row naming one city while pointing at another,
                    // and that survives every later worker run whose own fix comes back
                        // null — the widget would read "London" over Brighton's temperature.
                        WeatherUpdateScheduler.scheduleUserRefresh(context)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to process passive location update", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LocationUpdatesReceiver"
        private const val WORK_BUDGET_MS = 8_000L
        const val ACTION_LOCATION_UPDATE = "com.clockweather.app.ACTION_LOCATION_UPDATE"
    }
}
