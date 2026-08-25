package com.clockweather.app.presentation.widget.common

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import com.clockweather.app.R
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.PollenData
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.currentDisplayWeather
import com.clockweather.app.domain.model.locationReferenceDateTime
import com.clockweather.app.domain.model.weatherToday
import com.clockweather.app.presentation.detail.WeatherDetailActivity
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import java.time.LocalDate
import java.time.LocalDateTime

object WidgetDataBinder {
    private const val WidgetLocationMaxChars = 18

    fun bindSimpleClockViews(
        views: RemoteViews,
        hour: Int,
        minute: Int,
        is24h: Boolean = true,
        useHostDrivenClock: Boolean = true
    ) {
        val displayHour = if (is24h) hour
            else if (hour == 0) 12
            else if (hour > 12) hour - 12
            else hour

        val h1 = displayHour / 10
        val h2 = displayHour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        val hourFormat = if (is24h) "HH" else "hh"
        val ampmFormat = if (is24h) "" else "a"
        views.setCharSequence(R.id.clock_hour, "setFormat12Hour", hourFormat)
        views.setCharSequence(R.id.clock_hour, "setFormat24Hour", hourFormat)
        views.setCharSequence(R.id.clock_minute, "setFormat12Hour", "mm")
        views.setCharSequence(R.id.clock_minute, "setFormat24Hour", "mm")
        views.setCharSequence(R.id.ampm, "setFormat12Hour", ampmFormat)
        views.setCharSequence(R.id.ampm, "setFormat24Hour", ampmFormat)

        if (useHostDrivenClock) {
            views.setViewVisibility(R.id.clock_hour, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.clock_minute, android.view.View.VISIBLE)
            views.setTextViewText(R.id.digit_h1, " ")
            views.setTextViewText(R.id.digit_h2, " ")
            views.setTextViewText(R.id.digit_m1, " ")
            views.setTextViewText(R.id.digit_m2, " ")
        } else {
            views.setViewVisibility(R.id.clock_hour, android.view.View.GONE)
            views.setViewVisibility(R.id.clock_minute, android.view.View.GONE)
            views.setTextViewText(R.id.digit_h1, h1.toString())
            views.setTextViewText(R.id.digit_h2, h2.toString())
            views.setTextViewText(R.id.digit_m1, m1.toString())
            views.setTextViewText(R.id.digit_m2, m2.toString())
        }
    }

    fun bindWeatherViews(
        context: Context,
        views: RemoteViews,
        weatherData: WeatherData,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED,
        renderIcon: (Context, Int) -> Bitmap? = ::renderWidgetIconBitmap,
        referenceDateTime: LocalDateTime = weatherData.locationReferenceDateTime(),
        iconViewId: Int = R.id.weather_icon,
    ) {
        val currentDisplayWeather = weatherData.currentDisplayWeather(referenceDateTime)
        val location = weatherData.location
        val forecastAnchorDate = referenceDateTime.toLocalDate()
        val todayForecast = weatherData.dailyForecasts.firstOrNull { it.date == forecastAnchorDate }
            ?: weatherData.dailyForecasts.firstOrNull()

        val cityLabel = resolveWidgetLocationLabel(location, WidgetLocationMaxChars)
        val displayCondition = LocalDayNightDisplayMapper.mapCondition(
            condition = currentDisplayWeather.weatherCondition,
            currentTime = referenceDateTime.toLocalTime(),
            todayForecast = todayForecast,
        )
        val conditionLabel = context.getString(displayCondition.labelResId)
        views.setTextViewText(R.id.city_name, cityLabel)
        views.setTextViewText(R.id.city_name_top, cityLabel)
        views.setTextViewText(R.id.condition_text, conditionLabel)
        views.setTextViewText(R.id.condition_text_top, conditionLabel)
        setWidgetIcon(
            views,
            iconViewId,
            context,
            WeatherIconMapper.getDrawableResId(displayCondition, iconStyle),
            renderIcon,
        )
        val tempLabel = TemperatureFormatter.formatWithUnit(currentDisplayWeather.temperature, temperatureUnit)
        views.setTextViewText(R.id.current_temp, tempLabel)
        views.setTextViewText(R.id.current_temp_top, tempLabel)
        todayForecast?.let { forecast ->
            val tempFormat = if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit
            val highLowLabel = context.getString(tempFormat, forecast.temperatureMax) + "/" + context.getString(tempFormat, forecast.temperatureMin)
            views.setTextViewText(R.id.high_low, highLowLabel)
            views.setTextViewText(R.id.high_low_top, highLowLabel)
        }

        views.setViewVisibility(R.id.weather_card, android.view.View.VISIBLE)
    }

    fun bindWeatherUnavailableViews(
        context: Context,
        views: RemoteViews,
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED,
        renderIcon: (Context, Int) -> Bitmap? = ::renderWidgetIconBitmap,
        iconViewId: Int = R.id.weather_icon,
    ) {
        views.setTextViewText(R.id.city_name, context.getString(R.string.widget_weather_unavailable_title))
        views.setTextViewText(R.id.city_name_top, context.getString(R.string.widget_weather_unavailable_title))
        views.setTextViewText(R.id.condition_text, context.getString(R.string.widget_weather_unavailable_condition))
        views.setTextViewText(R.id.condition_text_top, context.getString(R.string.widget_weather_unavailable_condition))
        setWidgetIcon(
            views,
            iconViewId,
            context,
            WeatherIconMapper.getDrawableResId(com.clockweather.app.domain.model.WeatherCondition.PARTLY_CLOUDY_DAY, iconStyle),
            renderIcon,
        )
        views.setTextViewText(R.id.current_temp, context.getString(R.string.widget_weather_unavailable_temp))
        views.setTextViewText(R.id.current_temp_top, context.getString(R.string.widget_weather_unavailable_temp))
        views.setTextViewText(R.id.high_low, "")
        views.setTextViewText(R.id.high_low_top, "")
        views.setViewVisibility(R.id.weather_card, View.VISIBLE)
    }

    fun bindWeeklyForecastRows(
        context: Context,
        views: RemoteViews,
        weatherData: WeatherData,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
        today: LocalDate = weatherData.weatherToday(),
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED,
        // Row icons get their own, smaller cap — see [WidgetForecastIconMaxDimensionPx].
        renderIcon: (Context, Int) -> Bitmap? = { ctx, resId ->
            renderWidgetIconBitmap(ctx, resId, WidgetForecastIconMaxDimensionPx)
        },
    ) {
        data class RowIds(val name: Int, val icon: Int, val high: Int)
        val rows = listOf(
            RowIds(R.id.fday1_name, R.id.fday1_icon, R.id.fday1_high),
            RowIds(R.id.fday2_name, R.id.fday2_icon, R.id.fday2_high),
            RowIds(R.id.fday3_name, R.id.fday3_icon, R.id.fday3_high),
            RowIds(R.id.fday4_name, R.id.fday4_icon, R.id.fday4_high),
            RowIds(R.id.fday5_name, R.id.fday5_icon, R.id.fday5_high),
        )
        val tempFormat = if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit
        val forecastDays = selectForecastWidgetDays(weatherData, today)
        rows.forEachIndexed { i, r ->
            val forecast = forecastDays.getOrNull(i)
            if (forecast == null) {
                views.setViewVisibility(r.name, View.GONE)
                views.setViewVisibility(r.icon, View.GONE)
                views.setViewVisibility(r.high, View.GONE)
            } else {
                views.setViewVisibility(r.name, View.VISIBLE)
                views.setViewVisibility(r.icon, View.VISIBLE)
                views.setViewVisibility(r.high, View.VISIBLE)
                val dayLabel = DateFormatter.formatDayName(forecast.date)
                views.setTextViewText(r.name, dayLabel)
                setWidgetIcon(views, r.icon, context, WeatherIconMapper.getDrawableResId(forecast.weatherCondition, iconStyle), renderIcon)
                val high = context.getString(tempFormat, forecast.temperatureMax)
                val low = context.getString(tempFormat, forecast.temperatureMin)
                views.setTextViewText(r.high, "$high/$low")
            }
        }
        views.setViewVisibility(R.id.forecast_container, View.VISIBLE)
    }

    fun bindPollenBar(
        context: Context,
        views: RemoteViews,
        pollenData: PollenData?,
        showPollen: Boolean = true,
    ) {
        if (!showPollen || pollenData == null || !pollenData.hasData) {
            views.setViewVisibility(R.id.pollen_bar_container, View.GONE)
            return
        }

        val grassLevel = pollenData.grassPollen?.indexValue ?: 0
        val treeLevel = pollenData.treePollen?.indexValue ?: 0
        val weedLevel = pollenData.weedPollen?.indexValue ?: 0

        views.setImageViewBitmap(
            R.id.pollen_bar_grass_segments,
            renderPollenSegmentsBitmap(context, grassLevel)
        )
        views.setImageViewBitmap(
            R.id.pollen_bar_tree_segments,
            renderPollenSegmentsBitmap(context, treeLevel)
        )
        views.setImageViewBitmap(
            R.id.pollen_bar_weed_segments,
            renderPollenSegmentsBitmap(context, weedLevel)
        )

        views.setViewVisibility(R.id.pollen_bar_container, View.VISIBLE)
    }

    fun buildDetailPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WeatherDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal fun pollenLevelColor(level: Int): Int = when (level) {
    1, 2 -> 0xFF22C55E.toInt() // Green (Low)
    3 -> 0xFFEAB308.toInt()    // Amber / Yellow (Moderate)
    4 -> 0xFFF97316.toInt()    // Orange (High)
    5 -> 0xFFEF4444.toInt()    // Red (Very High)
    else -> 0x5594A3B8         // Inactive
}

internal fun renderPollenSegmentsBitmap(
    context: Context,
    level: Int,
    activeColor: Int = pollenLevelColor(level),
    inactiveColor: Int = 0x5594A3B8,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val segWidthPx = 7f * density
    val segHeightPx = 3.5f * density
    val gapPx = 2f * density
    val radiusPx = 1.75f * density
    val totalWidthPx = (segWidthPx * 5 + gapPx * 4).toInt().coerceAtLeast(1)
    val totalHeightPx = segHeightPx.toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(totalWidthPx, totalHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeColor
        style = Paint.Style.FILL
    }
    val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inactiveColor
        style = Paint.Style.FILL
    }

    val clampedLevel = level.coerceIn(0, 5)
    for (i in 0 until 5) {
        val left = i * (segWidthPx + gapPx)
        val paint = if (i < clampedLevel) activePaint else inactivePaint
        canvas.drawRoundRect(
            left, 0f, left + segWidthPx, segHeightPx,
            radiusPx, radiusPx, paint
        )
    }
    return bitmap
}

/**
 * Sets a widget icon as a [Bitmap] rendered in our own process.
 *
 * Home-screen widgets are inflated in the launcher's process. Vector drawables with
 * inline `aapt:attr` gradients (our weather icons) fail to inflate on some OEM / older
 * launchers (notably MIUI on Android 10), surfacing as "Can't load widget". Pre-rendering
 * to a bitmap sidesteps the launcher's vector inflater entirely. If rendering fails the icon
 * is left empty — passing the raw vector resource ID to the launcher would re-trigger the same
 * crash, so an empty icon is safer than a dead widget.
 */
internal fun setWidgetIcon(
    views: RemoteViews,
    viewId: Int,
    context: Context,
    drawableResId: Int,
    renderIcon: (Context, Int) -> Bitmap?,
) {
    val bitmap = renderIcon(context, drawableResId)
    if (bitmap != null) {
        views.setImageViewBitmap(viewId, bitmap)
    } else {
        // Fallback to setImageViewResource is removed.
        // If vector rendering fails (e.g. Xiaomi MIUI Android 10 failing to parse aapt:attr),
        // passing the vector drawable ID to the launcher will cause the launcher's RemoteViews
        // inflater to crash with the same error, resulting in a "Can't load widget" placeholder.
        // It is safer to leave the icon empty than to crash the entire widget.
    }
}

/** Largest dimension (px) of a pre-rendered widget icon bitmap. Caps the RemoteViews payload
 *  so six icons stay well under the launcher's ~1MB transaction budget on high-density devices. */
internal const val WidgetIconMaxDimensionPx = 192

/**
 * Target bitmap dimensions for a widget icon, derived from a drawable's intrinsic size.
 *
 * [Drawable.getIntrinsicWidth] on a vector is ALREADY density-scaled (dp x screen density),
 * so multiplying by density again produced multi-megabyte bitmaps (e.g. 726x682 from a 96dp
 * icon on a 2.75x device). That blew MIUI's RemoteViews transaction budget and surfaced as
 * "Can't load widget" on high-density Android 10 phones. We use the intrinsic pixel size
 * directly and cap the largest dimension to [maxDimensionPx], preserving aspect ratio.
 */
internal fun widgetIconTargetSize(
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    maxDimensionPx: Int = WidgetIconMaxDimensionPx,
): Pair<Int, Int> {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxDimensionPx
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxDimensionPx
    val largest = maxOf(w, h)
    if (largest <= maxDimensionPx) return w to h
    val scale = maxDimensionPx.toFloat() / largest
    return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
}

/**
 * Cap for the small day icons in a weekly forecast row.
 *
 * These render at a fraction of the hero icon's on-screen size, but without their own cap they
 * were rasterised at [WidgetIconMaxDimensionPx] like everything else. With the 512x512 CLAY_3D
 * source that is 147KB each, and six icons pushed an Extended widget to ~894KB per update —
 * against a launcher transaction budget of roughly 1MB. Capping the row icons cuts that by more
 * than half with no visible change at their display size.
 */
internal const val WidgetForecastIconMaxDimensionPx = 128

/** Renders a (possibly vector) drawable to a bitmap sized by [widgetIconTargetSize]. */
internal fun renderWidgetIconBitmap(
    context: Context,
    drawableResId: Int,
    maxDimensionPx: Int = WidgetIconMaxDimensionPx,
): Bitmap? {
    return try {
        val drawable = AppCompatResources.getDrawable(context, drawableResId)?.mutate() ?: return null
        val (w, h) = widgetIconTargetSize(drawable.intrinsicWidth, drawable.intrinsicHeight, maxDimensionPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bitmap
    } catch (e: Throwable) {
        null
    }
}

internal fun resolveWidgetLocationLabel(
    location: Location,
    maxChars: Int = 18
): String {
    val name = location.name.trim()
    val area = location.area?.trim()?.takeIf { it.isNotBlank() && it != name }
    return if (name.length > maxChars && area != null && area.length < name.length) {
        area
    } else {
        name
    }
}

internal fun selectForecastWidgetDays(
    weatherData: WeatherData,
    today: LocalDate = weatherData.weatherToday(),
): List<com.clockweather.app.domain.model.DailyForecast> {
    return weatherData.dailyForecasts
        .sortedBy { it.date }
        .filter { !it.date.isBefore(today) }
        .take(5)
}
