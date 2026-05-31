package com.clockweather.app.presentation.widget.common

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import com.clockweather.app.R
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.currentHourTemperature
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
        referenceDateTime: LocalDateTime = LocalDateTime.now(),
    ) {
        val current = weatherData.currentWeather
        val location = weatherData.location
        val todayForecast = weatherData.dailyForecasts.firstOrNull()
        val currentHourTemperature = weatherData.currentHourTemperature(referenceDateTime)

        views.setTextViewText(R.id.city_name, resolveWidgetLocationLabel(location, WidgetLocationMaxChars))
        views.setTextViewText(R.id.condition_text, context.getString(current.weatherCondition.labelResId))
        setWidgetIcon(
            views,
            R.id.weather_icon,
            context,
            WeatherIconMapper.getDrawableResId(current.weatherCondition, iconStyle),
            renderIcon,
        )
        views.setTextViewText(
            R.id.current_temp,
            TemperatureFormatter.formatWithUnit(currentHourTemperature, temperatureUnit)
        )
        todayForecast?.let { forecast ->
            val tempFormat = if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit
            views.setTextViewText(
                R.id.high_low,
                context.getString(tempFormat, forecast.temperatureMax) + "/" + context.getString(tempFormat, forecast.temperatureMin)
            )
        }

        views.setViewVisibility(R.id.weather_card, android.view.View.VISIBLE)
    }

    fun bindWeatherUnavailableViews(
        context: Context,
        views: RemoteViews,
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED,
        renderIcon: (Context, Int) -> Bitmap? = ::renderWidgetIconBitmap,
    ) {
        views.setTextViewText(R.id.city_name, context.getString(R.string.widget_weather_unavailable_title))
        views.setTextViewText(R.id.condition_text, context.getString(R.string.widget_weather_unavailable_condition))
        setWidgetIcon(
            views,
            R.id.weather_icon,
            context,
            WeatherIconMapper.getDrawableResId(com.clockweather.app.domain.model.WeatherCondition.PARTLY_CLOUDY_DAY, iconStyle),
            renderIcon,
        )
        views.setTextViewText(R.id.current_temp, context.getString(R.string.widget_weather_unavailable_temp))
        views.setTextViewText(R.id.high_low, "")
        views.setViewVisibility(R.id.weather_card, View.VISIBLE)
    }

    fun bindWeeklyForecastRows(
        context: Context,
        views: RemoteViews,
        weatherData: WeatherData,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
        today: LocalDate = LocalDate.now(),
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED,
        renderIcon: (Context, Int) -> Bitmap? = ::renderWidgetIconBitmap,
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
        val futureDays = selectForecastWidgetDays(weatherData)
        rows.forEachIndexed { i, r ->
            val forecast = futureDays.getOrNull(i)
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

    fun buildDetailPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WeatherDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WeatherDetailActivity.EXTRA_WIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Sets a widget icon as a [Bitmap] rendered in our own process.
 *
 * Home-screen widgets are inflated in the launcher's process. Vector drawables with
 * inline `aapt:attr` gradients (our weather icons) fail to inflate on some OEM / older
 * launchers (notably MIUI on Android 10), surfacing as "Can't load widget". Pre-rendering
 * to a bitmap sidesteps the launcher's vector inflater entirely. If rendering fails we fall
 * back to [RemoteViews.setImageViewResource] so behaviour is unchanged on capable launchers.
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
        views.setImageViewResource(viewId, drawableResId)
    }
}

/** Renders a (possibly vector) drawable to a bitmap at its intrinsic size scaled by density. */
internal fun renderWidgetIconBitmap(context: Context, drawableResId: Int): Bitmap? {
    return try {
        val drawable = AppCompatResources.getDrawable(context, drawableResId)?.mutate() ?: return null
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val w = (iw * density).toInt().coerceAtLeast(1)
        val h = (ih * density).toInt().coerceAtLeast(1)
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

internal fun selectForecastWidgetDays(weatherData: WeatherData): List<com.clockweather.app.domain.model.DailyForecast> {
    val forecastAnchorDate = weatherData.currentWeather.lastUpdated.toLocalDate()
    return weatherData.dailyForecasts
        .sortedBy { it.date }
        .filter { it.date.isAfter(forecastAnchorDate) }
        .take(5)
}
