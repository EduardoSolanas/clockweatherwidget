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

    fun updateWidget(appWidgetId: Int, isMinuteTick: Boolean = false) {
        // Run completely asynchronously
        scope.launch {
            try {
                val now = LocalTime.now()
                val currentEpochMinute = System.currentTimeMillis() / 60000L
                // Fetch prefs and weather on IO thread
                val prefs = entryPoint.dataStore().data.first()
                val is24h = prefs[booleanPreferencesKey("use_24h_clock")] ?: android.text.format.DateFormat.is24HourFormat(context)
                val showDate = prefs[booleanPreferencesKey("show_date_in_widget")] ?: true
                val tempUnitName = prefs[stringPreferencesKey("temperature_unit")] ?: TemperatureUnit.CELSIUS.name
                val tempUnit = runCatching { TemperatureUnit.valueOf(tempUnitName) }.getOrDefault(TemperatureUnit.CELSIUS)

                val clockThemeName = prefs[stringPreferencesKey("clock_theme")] ?: "dark"
                val theme = WidgetThemeSelector.getTheme(clockThemeName)
                val tileBgRes = theme.backgroundResId
                val digitColor = theme.textColor

                val tileSizeName = prefs[stringPreferencesKey("clock_tile_size")] ?: "MEDIUM"
                val tileSize = runCatching { ClockTileSize.valueOf(tileSizeName) }.getOrDefault(ClockTileSize.MEDIUM)
                val dimHeight = when (tileSize) {
                    ClockTileSize.SMALL -> com.clockweather.app.R.dimen.flip_digit_height_small
                    ClockTileSize.MEDIUM -> com.clockweather.app.R.dimen.flip_digit_height_medium
                    ClockTileSize.LARGE -> com.clockweather.app.R.dimen.flip_digit_height_large
                    ClockTileSize.EXTRA_LARGE -> com.clockweather.app.R.dimen.flip_digit_height_xl
                }
                val dimText = when (tileSize) {
                    ClockTileSize.SMALL -> com.clockweather.app.R.dimen.flip_text_size_small
                    ClockTileSize.MEDIUM -> com.clockweather.app.R.dimen.flip_text_size_medium
                    ClockTileSize.LARGE -> com.clockweather.app.R.dimen.flip_text_size_large
                    ClockTileSize.EXTRA_LARGE -> com.clockweather.app.R.dimen.flip_text_size_xl
                }
                
                // Build fresh RemoteViews
                val views = RemoteViews(context.packageName, layoutResId)
                
                try {
                    views.setOnClickPendingIntent(rootViewId, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                } catch (e: Exception) { /* ignore */ }

                // Bind clock. If it's a minute tick, we want the flip animation!
                WidgetDataBinder.bindClockViews(context, views, appWidgetId, now.hour, now.minute, is24h, isIncremental = isMinuteTick)

                listOf(
                    com.clockweather.app.R.id.digit_h1,
                    com.clockweather.app.R.id.digit_h2,
                    com.clockweather.app.R.id.digit_m1,
                    com.clockweather.app.R.id.digit_m2
                ).forEach { id ->
                    views.setInt(id, "setBackgroundResource", tileBgRes)
                    try {
                        views.setOnClickPendingIntent(id, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                    } catch (e: Exception) { /* ignore */ }

                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        views.setViewLayoutHeight(id, context.resources.getDimension(dimHeight), android.util.TypedValue.COMPLEX_UNIT_PX)
                    }
                    
                    val entryName = context.resources.getResourceEntryName(id)
                    for (i in 0..9) {
                        val childId = context.resources.getIdentifier("${entryName}_$i", "id", context.packageName)
                        if (childId != 0) {
                            views.setTextColor(childId, digitColor)
                            views.setTextViewTextSize(childId, android.util.TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimText))
                        }
                    }
                }
                
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
                views.setTextColor(com.clockweather.app.R.id.ampm, digitColor)

                // Bind date
                if (showDate) {
                    val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                    val fontSizeSp = prefs[floatPreferencesKey("date_font_size_sp")] ?: 15f
                    views.setTextViewText(dateViewId, dateStr)
                    views.setTextViewTextSize(dateViewId, android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                    views.setViewVisibility(dateViewId, View.VISIBLE)
                } else {
                    views.setViewVisibility(dateViewId, View.GONE)
                }

                // Bind clicks to prevent launcher hijacking (like opening Calendar)
                bindAllClicks(views, appWidgetId)

                // Weather
                val locationRepo = entryPoint.locationRepository()
                val weatherRepo = entryPoint.weatherRepository()
                val locations = locationRepo.getSavedLocations().first()
                val location = locations.firstOrNull() ?: locationRepo.getCurrentLocation()?.also {
                    locationRepo.saveLocation(it)
                }
                
                if (location == null) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    WidgetClockStateStore.markRendered(context, appWidgetId, currentEpochMinute)
                    return@launch
                }
                
                var weather = weatherRepo.getCachedWeatherData(location.id).first()
                if (weather == null && !isMinuteTick) { // Don't block minute ticks on network
                    try {
                        weatherRepo.refreshWeatherData(location)
                        weather = weatherRepo.getCachedWeatherData(location.id).first()
                    } catch (e: Exception) { }
                }

                if (weather != null) {
                    WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit)
                    bindExtra(views, weather, tempUnit, prefs)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
                WidgetClockStateStore.markRendered(context, appWidgetId, currentEpochMinute)
                Log.d(tag, "Widget $appWidgetId fully updated successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Widget update failed for widget $appWidgetId", e)
            }
        }
    }

    /**
     * Reuses full update function for clock ticks.
     */
    fun updateClockOnly(appWidgetId: Int) {
        Log.d(tag, "Updating clock only for widget $appWidgetId")
        scope.launch {
            try {
                val currentEpochMinute = System.currentTimeMillis() / 60000L
                val lastRenderedEpochMinute = WidgetClockStateStore.getLastRenderedEpochMinute(context, appWidgetId)
                val updateMode = WidgetClockUpdateModeResolver.resolve(lastRenderedEpochMinute, currentEpochMinute)
                if (updateMode == WidgetClockUpdateMode.FULL) {
                    Log.d(tag, "Falling back to full update for widget $appWidgetId. last=$lastRenderedEpochMinute current=$currentEpochMinute")
                    updateWidget(appWidgetId)
                    return@launch
                }

                // Fetch prefs and weather on IO thread
                val prefs = entryPoint.dataStore().data.first()
                val is24h = prefs[booleanPreferencesKey("use_24h_clock")] ?: android.text.format.DateFormat.is24HourFormat(context)
                
                val views = RemoteViews(context.packageName, layoutResId)
                val now = LocalTime.now()

                // Ensure all areas (digits, root, etc) remain bound to our app during partial updates
                bindAllClicks(views, appWidgetId)

                WidgetDataBinder.bindClockViews(
                    context, 
                    views, 
                    appWidgetId, 
                    now.hour, 
                    now.minute, 
                    is24h, 
                    isIncremental = true
                )

                // IMPORTANT: For minute ticks we MUST use partiallyUpdateAppWidget to prevent the 
                // launcher from completely destroying and recreating the widget hierarchy (which causes a full screen flicker).
                // `WidgetDataBinder` now guarantees we don't spam `setDisplayedChild` for identical digits, bypassing the Android infinite animation bug.
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                WidgetClockStateStore.markRendered(context, appWidgetId, currentEpochMinute)
                Log.d(tag, "Widget $appWidgetId clock-only update successful.")
            } catch (e: Exception) {
                Log.e(tag, "Clock-only update failed for widget $appWidgetId", e)
            }
        }
    }

    private fun bindAllClicks(views: RemoteViews, appWidgetId: Int) {
        val pendingIntent = WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId)
        try {
            val clickableIds = buildList {
                add(rootViewId)
                add(dateViewId)
                add(com.clockweather.app.R.id.weather_card)
                add(com.clockweather.app.R.id.city_name)
                add(com.clockweather.app.R.id.condition_text)
                add(com.clockweather.app.R.id.weather_icon)
                add(com.clockweather.app.R.id.current_temp)
                add(com.clockweather.app.R.id.high_low)
                add(com.clockweather.app.R.id.forecast_container)
                add(com.clockweather.app.R.id.fday1_name)
                add(com.clockweather.app.R.id.fday1_icon)
                add(com.clockweather.app.R.id.fday1_high)
                add(com.clockweather.app.R.id.fday2_name)
                add(com.clockweather.app.R.id.fday2_icon)
                add(com.clockweather.app.R.id.fday2_high)
                add(com.clockweather.app.R.id.fday3_name)
                add(com.clockweather.app.R.id.fday3_icon)
                add(com.clockweather.app.R.id.fday3_high)
                add(com.clockweather.app.R.id.fday4_name)
                add(com.clockweather.app.R.id.fday4_icon)
                add(com.clockweather.app.R.id.fday4_high)
                add(com.clockweather.app.R.id.fday5_name)
                add(com.clockweather.app.R.id.fday5_icon)
                add(com.clockweather.app.R.id.fday5_high)
                add(com.clockweather.app.R.id.fday6_name)
                add(com.clockweather.app.R.id.fday6_icon)
                add(com.clockweather.app.R.id.fday6_high)
                add(com.clockweather.app.R.id.fday7_name)
                add(com.clockweather.app.R.id.fday7_icon)
                add(com.clockweather.app.R.id.fday7_high)
                addAll(
                    listOf(
                        com.clockweather.app.R.id.digit_h1,
                        com.clockweather.app.R.id.digit_h2,
                        com.clockweather.app.R.id.digit_m1,
                        com.clockweather.app.R.id.digit_m2,
                        com.clockweather.app.R.id.colon,
                        com.clockweather.app.R.id.ampm
                    )
                )
                listOf("digit_h1", "digit_h2", "digit_m1", "digit_m2").forEach { prefix ->
                    for (i in 0..9) {
                        val childId = context.resources.getIdentifier("${prefix}_$i", "id", context.packageName)
                        if (childId != 0) add(childId)
                    }
                }
            }

            clickableIds.distinct().forEach { id ->
                views.setOnClickPendingIntent(id, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to bind clicks for widget $appWidgetId", e)
        }
    }
}
