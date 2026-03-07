package com.clockweather.app.presentation.widget.compact

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.presentation.widget.common.WidgetDataBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CompactWidgetUpdater(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val entryPoint: WidgetEntryPoint
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidget(appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_compact)

        // Click opens detail activity
        try {
            val pi = WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
        } catch (_: Exception) {}

        // Bind clock time as flip digit tiles (default 24h)
        val now = LocalTime.now()
        WidgetDataBinder.bindClockViews(views, now.hour, now.minute, true)

        // Push clock immediately so something always shows
        try { appWidgetManager.updateAppWidget(appWidgetId, views) } catch (_: Exception) { return }

        // Load weather data and preferences async
        scope.launch {
            try {
                val dataStore = entryPoint.dataStore()
                val is24h = dataStore.data.map { prefs ->
                    prefs[booleanPreferencesKey("use_24h_clock")] ?: true
                }.first()
                val showDate = dataStore.data.map { prefs ->
                    prefs[booleanPreferencesKey("show_date_in_widget")] ?: true
                }.first()
                val showToday = dataStore.data.map { prefs ->
                    prefs[booleanPreferencesKey("show_today_compact")] ?: true
                }.first()

                // Re-bind clock with correct 24h setting
                WidgetDataBinder.bindClockViews(views, now.hour, now.minute, is24h)

                // Bind date
                if (showDate) {
                    val dateStr = LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                    views.setTextViewText(R.id.widget_date, dateStr)
                    views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_date, View.GONE)
                }

                // Show/hide today weather card
                views.setViewVisibility(R.id.weather_card, if (showToday) View.VISIBLE else View.GONE)

                val locationRepo = entryPoint.locationRepository()
                val weatherRepo = entryPoint.weatherRepository()

                // Get or auto-detect location
                var locations = locationRepo.getSavedLocations().first()
                if (locations.isEmpty()) {
                    val gps = locationRepo.getCurrentLocation()
                    if (gps != null) {
                        locationRepo.saveLocation(gps)
                        locations = listOf(gps)
                    }
                }
                val location = locations.firstOrNull() ?: return@launch

                // Get cached weather
                val weather = weatherRepo.getCachedWeatherData(location.id).first()
                    ?: return@launch

                WidgetDataBinder.bindWeatherViews(context, views, weather, TemperatureUnit.CELSIUS)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {
                // Widget already shows clock — weather will update next cycle
            }
        }
    }
}
