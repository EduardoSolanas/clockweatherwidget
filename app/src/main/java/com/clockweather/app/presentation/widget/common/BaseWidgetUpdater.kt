package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.Gravity
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
    open val widgetPaddingDp: Float = 12f
    open val hasForecastViews: Boolean = false

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
                val weatherIconStyle = WeatherIconMapper.fromPreferenceValue(
                    prefs[com.clockweather.app.presentation.settings.SettingsViewModel.KEY_WEATHER_ICON_STYLE]
                        ?: com.clockweather.app.presentation.settings.SettingsViewModel.ICON_STYLE_GLASS
                )
                val widgetTextScale = (
                    prefs[com.clockweather.app.presentation.settings.SettingsViewModel.KEY_WIDGET_TEXT_SCALE]
                        ?: widgetTextScaleFromLegacyDateSize(
                            prefs[floatPreferencesKey("date_font_size_sp")]
                        )
                    ).coerceIn(0.75f, 1.15f)

                val clockThemeName = prefs[stringPreferencesKey("clock_theme")] ?: "light"
                val theme = WidgetThemeSelector.getTheme(clockThemeName)
                val tileBgRes = theme.backgroundResId
                val digitColor = ContextCompat.getColor(context, theme.textColorResId)

                val tileSizeName = prefs[stringPreferencesKey("clock_tile_size")] ?: "MEDIUM"
                val tileSize = runCatching { ClockTileSize.valueOf(tileSizeName) }.getOrDefault(ClockTileSize.MEDIUM)
                val clockTextPx = widgetTextPx(
                    context.resources,
                    WidgetTextRole.clock(tileSize),
                    widgetTextScale,
                )
                val dimHeight = when (tileSize) {
                    ClockTileSize.SMALL -> com.clockweather.app.R.dimen.flip_digit_height_small
                    ClockTileSize.MEDIUM -> com.clockweather.app.R.dimen.flip_digit_height_medium
                    ClockTileSize.LARGE -> com.clockweather.app.R.dimen.flip_digit_height_large
                    ClockTileSize.EXTRA_LARGE -> com.clockweather.app.R.dimen.flip_digit_height_xl
                }
                val fontScale = context.resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
                val heightPx = computeFlipTileHeightPx(
                    context.resources.getDimension(dimHeight), fontScale, widgetTextScale,
                )
                val gapPx = context.resources.getDimension(com.clockweather.app.R.dimen.flip_digit_gap)

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
                    com.clockweather.app.R.id.digit_m2,
                ).forEach { id ->
                    views.setInt(id, "setBackgroundResource", tileBgRes)
                    try {
                        views.setOnClickPendingIntent(id, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                    } catch (e: Exception) { /* ignore */ }

                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        views.setViewLayoutHeight(id, heightPx, android.util.TypedValue.COMPLEX_UNIT_PX)
                    }

                    views.setInt(id, "setGravity", Gravity.CENTER)
                    views.setTextColor(id, digitColor)
                    views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, clockTextPx)
                }
                listOf(
                    com.clockweather.app.R.id.clock_hour,
                    com.clockweather.app.R.id.clock_minute,
                ).forEach { id ->
                    try {
                        views.setOnClickPendingIntent(id, WidgetDataBinder.buildDetailPendingIntent(context, appWidgetId))
                    } catch (e: Exception) { /* ignore */ }
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        views.setViewLayoutHeight(id, heightPx, android.util.TypedValue.COMPLEX_UNIT_PX)
                    }
                    views.setInt(id, "setGravity", Gravity.CENTER)
                    views.setTextColor(id, digitColor)
                    views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, clockTextPx)
                }

                val colonSize = clockTextPx * 0.8f
                val paint = android.graphics.Paint().apply {
                    typeface = android.graphics.Typeface.MONOSPACE
                    textSize = clockTextPx
                    isFakeBoldText = true
                }
                val glyphAdvancePx = paint.measureText("0")
                val colonPaint = android.graphics.Paint().apply {
                    typeface = android.graphics.Typeface.MONOSPACE
                    textSize = colonSize
                    isFakeBoldText = true
                }
                val colonGlyphPx = colonPaint.measureText(":")
                val colonMarginPx = gapPx * 2
                val density = context.resources.displayMetrics.density
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val widgetWidthDp = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0
                ).toFloat()
                val widgetWidthPx = if (widgetWidthDp > 0) {
                    widgetWidthDp * density
                } else {
                    context.resources.displayMetrics.widthPixels * 0.9f
                }
                val widgetPaddingPx = widgetPaddingDp * density * 2
                val ampmMarginPx = 6f * density
                val clockBlockWidthPx = widgetWidthPx - widgetPaddingPx - ampmMarginPx
                val pairWidthPx = (clockBlockWidthPx - colonGlyphPx - colonMarginPx) / 2f
                val letterSpacing = computeFlipClockLetterSpacing(
                    pairWidthPx, gapPx, glyphAdvancePx, clockTextPx,
                )
                views.setFloat(com.clockweather.app.R.id.clock_hour, "setLetterSpacing", letterSpacing)
                views.setFloat(com.clockweather.app.R.id.clock_minute, "setLetterSpacing", letterSpacing)

                // Don't constrain weather_card to flip-tile height — it clips
                // the location / temperature text. Let it use its natural height.

                views.setTextViewTextSize(com.clockweather.app.R.id.colon, android.util.TypedValue.COMPLEX_UNIT_PX, colonSize)
                val ampmSize = widgetTextPx(context.resources, WidgetTextRole.clockCaption(tileSize), widgetTextScale)
                views.setTextViewTextSize(com.clockweather.app.R.id.ampm, android.util.TypedValue.COMPLEX_UNIT_PX, ampmSize)
                views.setTextColor(com.clockweather.app.R.id.colon, digitColor)
                views.setTextColor(com.clockweather.app.R.id.ampm, digitColor)

                if (showDate) {
                    val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                    views.setTextViewText(dateViewId, dateStr)
                    views.setTextViewTextSize(
                        dateViewId,
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                        widgetTextPx(context.resources, WidgetTextRole.EMPHASIS, widgetTextScale),
                    )
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
                    WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit, weatherIconStyle)
                    bindExtra(views, weather, tempUnit, prefs)
                } else {
                    WidgetDataBinder.bindWeatherUnavailableViews(context, views, weatherIconStyle)
                }
                applyWeatherTextSizing(views, widgetTextScale)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(tag, "Widget $appWidgetId updated.")
            } catch (e: Exception) {
                Log.e(tag, "Widget update failed for widget $appWidgetId", e)
            }
        }
    }

    private fun applyWeatherTextSizing(views: RemoteViews, widgetTextScale: Float) {
        views.setTextViewTextSize(com.clockweather.app.R.id.city_name, android.util.TypedValue.COMPLEX_UNIT_PX, widgetTextPx(context.resources, WidgetTextRole.EMPHASIS, widgetTextScale))
        views.setTextViewTextSize(com.clockweather.app.R.id.condition_text, android.util.TypedValue.COMPLEX_UNIT_PX, widgetTextPx(context.resources, WidgetTextRole.SECONDARY, widgetTextScale))
        views.setTextViewTextSize(com.clockweather.app.R.id.current_temp, android.util.TypedValue.COMPLEX_UNIT_PX, widgetTextPx(context.resources, WidgetTextRole.TEMPERATURE, widgetTextScale))
        views.setTextViewTextSize(com.clockweather.app.R.id.high_low, android.util.TypedValue.COMPLEX_UNIT_PX, widgetTextPx(context.resources, WidgetTextRole.BODY, widgetTextScale))

        if (hasForecastViews) {
            listOf(
                com.clockweather.app.R.id.fday1_name,
                com.clockweather.app.R.id.fday2_name,
                com.clockweather.app.R.id.fday3_name,
                com.clockweather.app.R.id.fday4_name,
                com.clockweather.app.R.id.fday5_name,
            ).forEach { id ->
                views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, widgetTextPx(context.resources, WidgetTextRole.FORECAST_LABEL, widgetTextScale))
            }

            listOf(
                com.clockweather.app.R.id.fday1_high,
                com.clockweather.app.R.id.fday2_high,
                com.clockweather.app.R.id.fday3_high,
                com.clockweather.app.R.id.fday4_high,
                com.clockweather.app.R.id.fday5_high,
            ).forEach { id ->
                views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, widgetTextPx(context.resources, WidgetTextRole.FORECAST_META, widgetTextScale))
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
                if (hasForecastViews) {
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
                }
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

private const val ODD_DIGIT_PANEL_TRANSLATION_DP = -1.5f
private const val EVEN_DIGIT_PANEL_TRANSLATION_DP = 1.5f

internal enum class DigitPanelCorrection(val translationDp: Float) {
    ODD(ODD_DIGIT_PANEL_TRANSLATION_DP),
    EVEN(EVEN_DIGIT_PANEL_TRANSLATION_DP),
}

internal enum class WidgetTextRole(val multiplier: Float) {
    FORECAST_META(0.80f),
    FORECAST_LABEL(0.88f),
    SECONDARY(0.88f),
    BODY(1.00f),
    EMPHASIS(1.08f),
    TEMPERATURE(1.85f),
    CLOCK_SMALL(3.20f),
    CLOCK_MEDIUM(4.00f),
    CLOCK_LARGE(4.50f),
    CLOCK_XL(5.00f),
    CLOCK_CAPTION_SMALL(0.75f),
    CLOCK_CAPTION_MEDIUM(0.90f),
    CLOCK_CAPTION_LARGE(1.00f),
    CLOCK_CAPTION_XL(1.15f);

    companion object {
        fun clock(tileSize: ClockTileSize): WidgetTextRole = when (tileSize) {
            ClockTileSize.SMALL -> CLOCK_SMALL
            ClockTileSize.MEDIUM -> CLOCK_MEDIUM
            ClockTileSize.LARGE -> CLOCK_LARGE
            ClockTileSize.EXTRA_LARGE -> CLOCK_XL
        }

        fun clockCaption(tileSize: ClockTileSize): WidgetTextRole = when (tileSize) {
            ClockTileSize.SMALL -> CLOCK_CAPTION_SMALL
            ClockTileSize.MEDIUM -> CLOCK_CAPTION_MEDIUM
            ClockTileSize.LARGE -> CLOCK_CAPTION_LARGE
            ClockTileSize.EXTRA_LARGE -> CLOCK_CAPTION_XL
        }
    }
}

internal fun widgetSystemBaseTextPx(resources: Resources): Float {
    val fontScale = resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
    return 14f * resources.displayMetrics.density * fontScale
}

internal fun widgetTextPx(
    resources: Resources,
    role: WidgetTextRole,
    settingsScale: Float = 1f,
): Float = widgetSystemBaseTextPx(resources) * role.multiplier * settingsScale

internal fun widgetTextScaleFromLegacyDateSize(legacyDateSizeSp: Float?): Float =
    ((legacyDateSizeSp ?: 15f) / 15f).coerceIn(0.75f, 1.15f)

internal fun widgetDigitOffsetPx(
    resources: Resources,
    correction: DigitPanelCorrection,
): Float = correction.translationDp * resources.displayMetrics.density

internal fun computeFlipTileHeightPx(
    baseDimenPx: Float,
    fontScale: Float,
    widgetTextScale: Float,
): Float = baseDimenPx * fontScale * widgetTextScale

internal fun computeFlipClockLetterSpacing(
    pairWidthPx: Float,
    gapPx: Float,
    glyphAdvancePx: Float,
    fontSizePx: Float,
): Float {
    val tileCenterDistancePx = (pairWidthPx + gapPx) / 2f
    return ((tileCenterDistancePx - glyphAdvancePx) / fontSizePx).coerceAtLeast(0f)
}
