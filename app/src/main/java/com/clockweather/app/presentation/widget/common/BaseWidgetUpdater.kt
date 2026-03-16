package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.ClockTileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared base for all widget updaters.
 * Handles: clock binding, single DataStore read, location/weather fetch, error logging.
 * Subclasses only override layout ID, date/root view IDs, and extra weather binding.
 */
abstract class BaseWidgetUpdater(
    protected val context: Context,
    protected val appWidgetManager: AppWidgetManager,
    protected val entryPoint: WidgetEntryPoint
) {
    private val tag = this::class.simpleName ?: "WidgetUpdater"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    abstract val layoutResId: Int
    abstract val rootViewId: Int
    abstract val dateViewId: Int

    /** Called after weather data is available. Subclasses apply their specific bindings. */
    abstract fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences)

    fun updateWidget(appWidgetId: Int) {
        val views = RemoteViews(context.packageName, layoutResId)
        val now = LocalTime.now()

        // Set click → open detail activity
        try {
            views.setOnClickPendingIntent(rootViewId, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
        } catch (e: Exception) {
            Log.w(tag, "Failed to set click intent", e)
        }

        // Bind clock immediately so something shows instantly
        WidgetDataBinder.bindClockViews(context, views, appWidgetId, now.hour, now.minute, is24h = true)

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.w(tag, "Failed initial widget push", e)
            return
        }

        // Load prefs + weather data asynchronously
        scope.launch {
            try {
                // Single DataStore read — all prefs from one snapshot
                val prefs = entryPoint.dataStore().data.first()
                val is24h = prefs[booleanPreferencesKey("use_24h_clock")] ?: true
                val showDate = prefs[booleanPreferencesKey("show_date_in_widget")] ?: true
                val tempUnitName = prefs[stringPreferencesKey("temperature_unit")] ?: TemperatureUnit.CELSIUS.name
                val tempUnit = runCatching { TemperatureUnit.valueOf(tempUnitName) }.getOrDefault(TemperatureUnit.CELSIUS)

                // Re-bind clock with correct 24h setting
                WidgetDataBinder.bindClockViews(context, views, appWidgetId, now.hour, now.minute, is24h)

                // Apply clock tile theme (dark = black bg/white text, light = white bg/black text)
                val clockTheme = prefs[stringPreferencesKey("clock_theme")] ?: "dark"
                val (tileBgRes, digitColor, colonColor) = if (clockTheme == "light") {
                    Triple(com.clockweather.app.R.drawable.flip_digit_bg_light, Color.BLACK, 0xCC000000.toInt())
                } else {
                    Triple(com.clockweather.app.R.drawable.flip_digit_bg, Color.WHITE, 0x80FFFFFF.toInt())
                }

                val tileSizeName = prefs[stringPreferencesKey("clock_tile_size")] ?: "MEDIUM"
                val tileSize = runCatching { ClockTileSize.valueOf(tileSizeName) }
                    .getOrDefault(ClockTileSize.MEDIUM)

                val (dimWidth, dimHeight, dimText) = when (tileSize) {
                    ClockTileSize.SMALL -> Triple(com.clockweather.app.R.dimen.flip_digit_width_small, com.clockweather.app.R.dimen.flip_digit_height_small, com.clockweather.app.R.dimen.flip_text_size_small)
                    ClockTileSize.MEDIUM -> Triple(com.clockweather.app.R.dimen.flip_digit_width_medium, com.clockweather.app.R.dimen.flip_digit_height_medium, com.clockweather.app.R.dimen.flip_text_size_medium)
                    ClockTileSize.LARGE -> Triple(com.clockweather.app.R.dimen.flip_digit_width_large, com.clockweather.app.R.dimen.flip_digit_height_large, com.clockweather.app.R.dimen.flip_text_size_large)
                    ClockTileSize.EXTRA_LARGE -> Triple(com.clockweather.app.R.dimen.flip_digit_width_xl, com.clockweather.app.R.dimen.flip_digit_height_xl, com.clockweather.app.R.dimen.flip_text_size_xl)
                }

                listOf(
                    com.clockweather.app.R.id.digit_h1,
                    com.clockweather.app.R.id.digit_h2,
                    com.clockweather.app.R.id.digit_m1,
                    com.clockweather.app.R.id.digit_m2
                ).forEach { id ->
                    views.setInt(id, "setBackgroundResource", tileBgRes)

                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        // Width is controlled by layout_weight in XML (tiles fill available space)
                        views.setViewLayoutHeight(id, context.resources.getDimension(dimHeight), android.util.TypedValue.COMPLEX_UNIT_PX)
                    }
                    
                    // set text color and size for 0..9 children inside the ViewFlipper
                    val entryName = context.resources.getResourceEntryName(id)
                    for (i in 0..9) {
                        val childId = context.resources.getIdentifier("${entryName}_$i", "id", context.packageName)
                        if (childId != 0) {
                            views.setTextColor(childId, digitColor)
                            views.setTextViewTextSize(childId, android.util.TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimText))
                        }
                    }
                }
                views.setTextColor(com.clockweather.app.R.id.ampm, digitColor)
                
                // Scale colon and ampm
                val colonSize = context.resources.getDimension(dimText) * 0.8f
                views.setTextViewTextSize(com.clockweather.app.R.id.colon, android.util.TypedValue.COMPLEX_UNIT_PX, colonSize)
                
                val ampmSize = when (tileSize) {
                    ClockTileSize.SMALL -> 10f
                    ClockTileSize.MEDIUM -> 12f
                    ClockTileSize.LARGE -> 14f
                    ClockTileSize.EXTRA_LARGE -> 16f
                }
                views.setTextViewTextSize(com.clockweather.app.R.id.ampm, android.util.TypedValue.COMPLEX_UNIT_SP, ampmSize)
                views.setTextColor(com.clockweather.app.R.id.colon, digitColor)

                // Bind date
                if (showDate) {
                    val dateStr = LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                    val fontSizeSp = prefs[floatPreferencesKey("date_font_size_sp")] ?: 15f
                    views.setTextViewText(dateViewId, dateStr)
                    views.setTextViewTextSize(dateViewId, android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                    views.setViewVisibility(dateViewId, View.VISIBLE)
                } else {
                    views.setViewVisibility(dateViewId, View.GONE)
                }

                // Resolve location
                val locationRepo = entryPoint.locationRepository()
                val weatherRepo = entryPoint.weatherRepository()

                var locations = locationRepo.getSavedLocations().first()
                if (locations.isEmpty()) {
                    val gps = locationRepo.getCurrentLocation()
                    if (gps != null) {
                        locationRepo.saveLocation(gps)
                        locations = listOf(gps)
                    }
                }
                val location = locations.firstOrNull() ?: run {
                    Log.w(tag, "No location for widget $appWidgetId — clock only")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                // Try cache first; if empty (e.g. fresh install), do an immediate network fetch
                var weather = weatherRepo.getCachedWeatherData(location.id).first()
                if (weather == null) {
                    Log.i(tag, "Cache empty for ${location.name}, fetching from network…")
                    try {
                        weatherRepo.refreshWeatherData(location)
                        weather = weatherRepo.getCachedWeatherData(location.id).first()
                    } catch (e: Exception) {
                        Log.w(tag, "Network fetch failed, showing clock only", e)
                    }
                }

                if (weather != null) {
                    WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit)
                    bindExtra(views, weather, tempUnit, prefs)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)

            } catch (e: Exception) {
                Log.w(tag, "Widget update failed for widget $appWidgetId", e)
            }
        }
    }
}
