package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.ClockTileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared base for all widget updaters.
 * Handles: clock binding, single DataStore read, location/weather fetch, error logging.
 * Subclasses only override layout ID, date/root view IDs, and extra weather binding.
 *
 * Clock time is rendered by the TextClock views in the layout — no manual digit pushing needed.
 */
abstract class BaseWidgetUpdater(
    protected val context: Context,
    protected val appWidgetManager: AppWidgetManager,
    protected val entryPoint: WidgetEntryPoint
) {
    private val tag = this::class.simpleName ?: "WidgetUpdater"

    abstract val layoutResId: Int
    abstract val rootViewId: Int
    abstract val dateViewId: Int
    open val minimumFutureForecastDaysRequired: Int = 0

    /** Called after weather data is available. Subclasses apply their specific bindings. */
    abstract fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences)

    suspend fun updateWidget(
        appWidgetId: Int,
        allowWeatherRefresh: Boolean = true
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "updateWidget id=$appWidgetId")
                val snapshot = ClockSnapshot.now()
                val now = snapshot.localTime
                val prefs = entryPoint.dataStore().data.first()
                val is24h = prefs[booleanPreferencesKey("use_24h_clock")] ?: android.text.format.DateFormat.is24HourFormat(context)
                val showDate = prefs[booleanPreferencesKey("show_date_in_widget")] ?: true
                val tempUnitName = prefs[stringPreferencesKey("temperature_unit")] ?: TemperatureUnit.CELSIUS.name
                val tempUnit = runCatching { TemperatureUnit.valueOf(tempUnitName) }.getOrDefault(TemperatureUnit.CELSIUS)

                val clockThemeName = prefs[stringPreferencesKey("clock_theme")] ?: "light"
                val theme = WidgetThemeSelector.getTheme(clockThemeName)
                val tileBgRes = theme.backgroundResId
                val digitColor = ContextCompat.getColor(context, theme.textColorResId)

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

                val views = RemoteViews(context.packageName, layoutResId)

                try {
                    views.setOnClickPendingIntent(rootViewId, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                } catch (e: Exception) { /* ignore */ }

                // TextClock handles live minute-by-minute updates automatically.
                // This call sets the format strings and makes the TextClock views visible.
                WidgetDataBinder.bindSimpleClockViews(
                    views,
                    now.hour,
                    now.minute,
                    is24h,
                    useHostDrivenClock = true,
                )

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

                    views.setTextColor(id, digitColor)
                    views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimText))
                }
                listOf(
                    com.clockweather.app.R.id.clock_hour,
                    com.clockweather.app.R.id.clock_minute,
                ).forEach { id ->
                    try {
                        views.setOnClickPendingIntent(id, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                    } catch (e: Exception) { /* ignore */ }
                    views.setTextColor(id, digitColor)
                    views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimText))
                }

                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    views.setViewLayoutHeight(
                        com.clockweather.app.R.id.weather_card,
                        context.resources.getDimension(dimHeight),
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                    )
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

                if (showDate) {
                    val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                    val fontSizeSp = prefs[floatPreferencesKey("date_font_size_sp")] ?: 15f
                    views.setTextViewText(dateViewId, dateStr)
                    views.setTextViewTextSize(dateViewId, android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                    views.setViewVisibility(dateViewId, View.VISIBLE)
                } else {
                    views.setViewVisibility(dateViewId, View.GONE)
                }

                bindAllClicks(views, appWidgetId)

                val locationRepo = entryPoint.locationRepository()
                val weatherRepo = entryPoint.weatherRepository()
                val locations = locationRepo.getSavedLocations().first()
                val detectedLocation = if (locations.isEmpty()) {
                    withTimeoutOrNull(6_000L) { locationRepo.getCurrentLocation() }
                } else {
                    null
                }
                val location = locations.firstOrNull() ?: run {
                    val candidate = detectedLocation ?: locationRepo.getFallbackLocation()
                    val savedId = locationRepo.saveLocation(candidate)
                    if (candidate.id == 0L) candidate.copy(id = savedId) else candidate
                }

                var weather = weatherRepo.getWeatherData(location).first()
                if (allowWeatherRefresh && shouldRefreshWeather(weather, LocalDate.now(), minimumFutureForecastDaysRequired)) {
                    try {
                        val forecastDays = requiredForecastDaysForRefresh(
                            prefs[com.clockweather.app.presentation.settings.SettingsViewModel.KEY_FORECAST_DAYS] ?: 7,
                            minimumFutureForecastDaysRequired,
                        )
                        weatherRepo.refreshWeatherData(location, forecastDays)
                        weather = weatherRepo.getWeatherData(location).first()
                    } catch (e: Exception) { }
                }

                if (weather != null) {
                    WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit)
                    bindExtra(views, weather, tempUnit, prefs)
                } else {
                    WidgetDataBinder.bindWeatherUnavailableViews(context, views)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(tag, "Widget $appWidgetId updated.")
            } catch (e: Exception) {
                Log.e(tag, "Widget update failed for widget $appWidgetId", e)
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
                addAll(
                    listOf(
                        com.clockweather.app.R.id.digit_h1,
                        com.clockweather.app.R.id.digit_h2,
                        com.clockweather.app.R.id.digit_m1,
                        com.clockweather.app.R.id.digit_m2,
                        com.clockweather.app.R.id.clock_hour_pair,
                        com.clockweather.app.R.id.clock_minute_pair,
                        com.clockweather.app.R.id.clock_hour,
                        com.clockweather.app.R.id.clock_minute,
                        com.clockweather.app.R.id.colon,
                        com.clockweather.app.R.id.ampm
                    )
                )
            }

            clickableIds.distinct().forEach { id ->
                views.setOnClickPendingIntent(id, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to bind clicks for widget $appWidgetId", e)
        }
    }
}

internal fun shouldRefreshWeather(
    weather: WeatherData?,
    today: LocalDate,
    minimumFutureForecastDaysRequired: Int = 0,
): Boolean {
    if (weather == null) return true
    if (weather.currentWeather.lastUpdated.toLocalDate().isBefore(today)) return true
    if (weather.dailyForecasts.firstOrNull()?.date?.isBefore(today) == true) return true
    val futureDayCount = weather.dailyForecasts.count { it.date.isAfter(today) }
    return futureDayCount < minimumFutureForecastDaysRequired
}

internal fun requiredForecastDaysForRefresh(
    requestedForecastDays: Int,
    minimumFutureForecastDaysRequired: Int,
): Int = maxOf(requestedForecastDays, minimumFutureForecastDaysRequired + 1)
