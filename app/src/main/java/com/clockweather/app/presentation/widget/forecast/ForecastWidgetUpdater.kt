package com.clockweather.app.presentation.widget.forecast

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

class ForecastWidgetUpdater(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val entryPoint: WidgetEntryPoint
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidget(appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_forecast)

        try {
            val pi = WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
        } catch (_: Exception) {}

        val now = LocalTime.now()
        WidgetDataBinder.bindClockViews(views, now.hour, now.minute, true)

        try { appWidgetManager.updateAppWidget(appWidgetId, views) } catch (_: Exception) { return }

        scope.launch {
            try {
                val dataStore = entryPoint.dataStore()
                val is24h = dataStore.data.map { it[booleanPreferencesKey("use_24h_clock")] ?: true }.first()
                val showDate = dataStore.data.map { it[booleanPreferencesKey("show_date_in_widget")] ?: true }.first()
                val tempUnitName = dataStore.data.map { it[androidx.datastore.preferences.core.stringPreferencesKey("temperature_unit")] ?: "CELSIUS" }.first()
                val tempUnit = runCatching { TemperatureUnit.valueOf(tempUnitName) }.getOrDefault(TemperatureUnit.CELSIUS)

                WidgetDataBinder.bindClockViews(views, now.hour, now.minute, is24h)

                if (showDate) {
                    val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                    views.setTextViewText(R.id.widget_date, dateStr)
                    views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_date, View.GONE)
                }

                val locationRepo = entryPoint.locationRepository()
                val weatherRepo = entryPoint.weatherRepository()

                var locations = locationRepo.getSavedLocations().first()
                if (locations.isEmpty()) {
                    val gps = locationRepo.getCurrentLocation()
                    if (gps != null) { locationRepo.saveLocation(gps); locations = listOf(gps) }
                }
                val location = locations.firstOrNull() ?: return@launch
                val weather = weatherRepo.getCachedWeatherData(location.id).first() ?: return@launch

                WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit)
                WidgetDataBinder.bindWeeklyForecastRows(context, views, weather, tempUnit)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {}
        }
    }
}

