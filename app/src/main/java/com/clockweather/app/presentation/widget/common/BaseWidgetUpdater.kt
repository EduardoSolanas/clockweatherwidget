package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
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
import com.clockweather.app.util.WidgetPrefsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

    abstract val layoutResId: Int
    abstract val rootViewId: Int
    abstract val dateViewId: Int
    open val usesSimpleClockDigits: Boolean = false
    open val usesAtomicClockText: Boolean = false

    /**
     * Resolves whether simple (non-animated) clock digits should be used.
     * Returns true when the user has disabled the flip animation in settings,
     * OR when the subclass has hardcoded usesSimpleClockDigits = true.
     */
    protected fun resolveUsesSimpleDigits(prefs: Preferences): Boolean {
        if (usesSimpleClockDigits || usesAtomicClockText) return true
        val flipEnabled = prefs[booleanPreferencesKey("flip_animation_enabled")] ?: true
        return !flipEnabled
    }

    /** Called after weather data is available. Subclasses apply their specific bindings. */
    abstract fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences)

    suspend fun updateWidget(
        appWidgetId: Int,
        isMinuteTick: Boolean = false,
        allowWeatherRefresh: Boolean = !isMinuteTick
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "updateWidget id=$appWidgetId isMinuteTick=$isMinuteTick usesSimpleClockDigits=$usesSimpleClockDigits")
                val now = LocalTime.now()
                val currentEpochMinute = System.currentTimeMillis() / 60000L
                // Fetch prefs and weather on IO thread
                val prefs = entryPoint.dataStore().data.first()
                val useSimple = resolveUsesSimpleDigits(prefs)
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

                // Check if this widget has been rendered before — if so we can use
                // partiallyUpdateAppWidget() which MERGES actions with existing host
                // state, avoiding the ViewFlipper reset/flicker caused by a full
                // updateAppWidget() replacement.
                val prevDigits = WidgetClockStateStore.getLastDigits(context, appWidgetId)
                val isFirstRender = prevDigits == null

                // Build fresh RemoteViews
                val views = RemoteViews(context.packageName, layoutResId)

                try {
                    views.setOnClickPendingIntent(rootViewId, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                } catch (e: Exception) { /* ignore */ }

                val useIncrementalClockBinding = shouldUseIncrementalClockBinding(
                    isFirstRender = isFirstRender,
                    isMinuteTick = isMinuteTick
                )

                if (!useIncrementalClockBinding) {
                    // First render and non-minute refreshes (time/timezone/weather/manual)
                    // must set all digits to keep flipper state synchronized.
                    if (usesAtomicClockText) {
                        WidgetDataBinder.bindAtomicClockViews(views, now.hour, now.minute, is24h)
                    } else if (usesSimpleClockDigits) {
                        WidgetDataBinder.bindSimpleClockViews(views, now.hour, now.minute, is24h)
                    } else if (useSimple) {
                        WidgetDataBinder.bindStaticClockViews(context, views, now.hour, now.minute, is24h)
                    } else {
                        WidgetDataBinder.bindClockViews(context, views, appWidgetId, now.hour, now.minute, is24h, isIncremental = false)
                    }
                } else {
                    // Subsequent render — only touch digits that actually changed.
                    // This prevents ViewFlipper flicker when only weather/date changed.
                    if (usesAtomicClockText) {
                        WidgetDataBinder.bindAtomicClockViews(views, now.hour, now.minute, is24h)
                    } else if (usesSimpleClockDigits) {
                        WidgetDataBinder.bindSimpleClockViews(views, now.hour, now.minute, is24h)
                    } else if (useSimple) {
                        WidgetDataBinder.bindStaticClockViews(context, views, now.hour, now.minute, is24h)
                    } else {
                        WidgetDataBinder.bindClockViews(context, views, appWidgetId, now.hour, now.minute, is24h, isIncremental = true, prevDigits = prevDigits)
                    }
                }

                // Store the rendered digits so the next update diffs correctly
                WidgetClockStateStore.saveLastDigits(context, appWidgetId, DigitState.from(now.hour, now.minute, is24h))

                if (!usesAtomicClockText) {
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

                    if (usesSimpleClockDigits) {
                        // Layout has plain TextViews — style them directly
                        views.setTextColor(id, digitColor)
                        views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimText))
                    } else {
                        // Layout has ViewFlippers — style each child TextView
                        val entryName = context.resources.getResourceEntryName(id)
                        for (i in 0..9) {
                            val childId = context.resources.getIdentifier("${entryName}_$i", "id", context.packageName)
                            if (childId != 0) {
                                views.setTextColor(childId, digitColor)
                                views.setTextViewTextSize(childId, android.util.TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimText))
                            }
                        }
                    }
                }
                }

                if (usesAtomicClockText) {
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
                    if (isFirstRender) {
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } else {
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                    }
                    WidgetClockStateStore.markRendered(context, appWidgetId, currentEpochMinute)
                    WidgetClockStateStore.markBaselineReady(context, appWidgetId)
                    return@withContext
                }

                var weather = weatherRepo.getCachedWeatherData(location.id).first()
                if (weather == null && allowWeatherRefresh) {
                    try {
                        weatherRepo.refreshWeatherData(location)
                        weather = weatherRepo.getCachedWeatherData(location.id).first()
                    } catch (e: Exception) { }
                }

                if (weather != null) {
                    WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit)
                    bindExtra(views, weather, tempUnit, prefs)
                }

                if (isFirstRender) {
                    // First render — full replacement to establish base widget state
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } else {
                    // Subsequent render — merge with existing state so unchanged
                    // ViewFlipper digits are not reset (no flicker)
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                }
                WidgetClockStateStore.markRendered(context, appWidgetId, currentEpochMinute)
                WidgetClockStateStore.markBaselineReady(context, appWidgetId)
                Log.d(tag, "Widget $appWidgetId updated (firstRender=$isFirstRender).")
            } catch (e: Exception) {
                Log.e(tag, "Widget update failed for widget $appWidgetId", e)
            }
        }
    }

    /**
     * Reuses full update function for clock ticks.
     */
    suspend fun updateClockOnly(
        appWidgetId: Int,
        allowAnimation: Boolean = false
    ) {
        Log.d(
            tag,
            "Updating clock only for widget $appWidgetId usesSimpleClockDigits=$usesSimpleClockDigits usesAtomicClockText=$usesAtomicClockText allowAnimation=$allowAnimation"
        )
        withContext(Dispatchers.IO) {
            try {
                // Use in-memory cache instead of disk I/O for the hot minute-tick path
                val prefs = WidgetPrefsCache.get(entryPoint.dataStore())
                val useSimple = resolveUsesSimpleDigits(prefs)

                if (!useSimple && !WidgetClockStateStore.isBaselineReady(context, appWidgetId)) {
                    // One-time full refresh without setDisplayedChild to reset host-side merged actions.
                    updateWidget(appWidgetId, allowWeatherRefresh = false)
                    WidgetClockStateStore.markBaselineReady(context, appWidgetId)
                    Log.d(tag, "Widget $appWidgetId baseline full refresh completed.")
                    return@withContext
                }

                val currentEpochMinute = System.currentTimeMillis() / 60000L
                val lastRenderedEpochMinute = WidgetClockStateStore.getLastRenderedEpochMinute(context, appWidgetId)

                // Dedup: if this minute was already rendered (e.g. syncClockNow + TIME_TICK
                // both fired), skip entirely to avoid a wasteful full rebuild.
                if (lastRenderedEpochMinute != null && lastRenderedEpochMinute == currentEpochMinute) {
                    Log.d(tag, "Widget $appWidgetId already rendered for minute $currentEpochMinute — skipping")
                    return@withContext
                }

                val suppressAnimationOnce = WidgetClockStateStore.shouldSuppressAnimation(
                    context,
                    appWidgetId,
                    currentEpochMinute
                )
                val updateMode = WidgetClockUpdateModeResolver.resolve(lastRenderedEpochMinute, currentEpochMinute)
                Log.d(tag, "Clock-only mode for $appWidgetId: last=$lastRenderedEpochMinute current=$currentEpochMinute mode=$updateMode useSimple=$useSimple")
                if (updateMode == WidgetClockUpdateMode.FULL) {
                    Log.d(tag, "Falling back to full update for widget $appWidgetId. last=$lastRenderedEpochMinute current=$currentEpochMinute")
                    updateWidget(appWidgetId, allowWeatherRefresh = false)
                    return@withContext
                }

                val is24h = prefs[booleanPreferencesKey("use_24h_clock")] ?: android.text.format.DateFormat.is24HourFormat(context)
                
                val views = RemoteViews(context.packageName, layoutResId)
                val now = LocalTime.now()

                // C1: read stored digits for accurate incremental diff (fixes Doze-gap off-by-N flips)
                val prevDigits = WidgetClockStateStore.getLastDigits(context, appWidgetId)

                // B5: Don't rebind click actions on every tick — they are already set by the last
                // full updateWidget() call. Partial updates merge actions, so they are preserved.

                val renderPath = when {
                    usesAtomicClockText -> "atomic_text_no_animation"
                    usesSimpleClockDigits -> "simple_text_no_animation"
                    useSimple -> "static_viewflipper_no_animation"
                    suppressAnimationOnce -> "full_visibility_no_animation:suppression_window"
                    !allowAnimation -> "full_visibility_no_animation:allowAnimation_false"
                    else -> "incremental_flip_animation"
                }
                Log.d(
                    tag,
                    "CLOCK_TRACE updateClockOnly id=$appWidgetId minute=$currentEpochMinute " +
                        "path=$renderPath last=$lastRenderedEpochMinute suppressAnimationOnce=$suppressAnimationOnce"
                )

                when {
                    usesAtomicClockText -> {
                        WidgetDataBinder.bindAtomicClockViews(views, now.hour, now.minute, is24h)
                    }
                    usesSimpleClockDigits -> {
                        // Layout has plain TextViews
                        WidgetDataBinder.bindSimpleClockViews(views, now.hour, now.minute, is24h)
                    }
                    useSimple -> {
                        // Layout has ViewFlippers but flip animation is disabled
                        WidgetDataBinder.bindStaticClockViews(context, views, now.hour, now.minute, is24h)
                    }
                    suppressAnimationOnce || !allowAnimation -> {
                        WidgetDataBinder.bindClockViews(
                            context,
                            views,
                            appWidgetId,
                            now.hour,
                            now.minute,
                            is24h,
                            isIncremental = false
                        )
                    }
                    else -> {
                        WidgetDataBinder.bindClockViews(
                            context,
                            views,
                            appWidgetId,
                            now.hour,
                            now.minute,
                            is24h,
                            isIncremental = true,
                            prevDigits = prevDigits   // C1: accurate digit diff
                        )
                    }
                }

                // C1: persist the just-rendered digits for the next tick's diff
                val newDigits = DigitState.from(now.hour, now.minute, is24h)
                WidgetClockStateStore.saveLastDigits(context, appWidgetId, newDigits)

                // B5: partiallyUpdateAppWidget for BOTH paths — only the changed digit
                //     views are serialised; full weather/date/click state is untouched.
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                WidgetClockStateStore.markRendered(context, appWidgetId, currentEpochMinute)
                Log.d(tag, "Widget $appWidgetId clock-only update successful (useSimple=$useSimple).")
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

internal fun shouldUseIncrementalClockBinding(
    isFirstRender: Boolean,
    isMinuteTick: Boolean
): Boolean = !isFirstRender && isMinuteTick
