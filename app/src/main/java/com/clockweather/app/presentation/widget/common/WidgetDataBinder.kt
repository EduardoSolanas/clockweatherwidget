package com.clockweather.app.presentation.widget.common

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.clockweather.app.R
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.presentation.detail.WeatherDetailActivity
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import java.time.LocalDate

object WidgetDataBinder {

    fun bindSimpleClockViews(
        views: RemoteViews,
        hour: Int,
        minute: Int,
        is24h: Boolean = true
    ) {
        val displayHour = if (is24h) hour
            else if (hour == 0) 12
            else if (hour > 12) hour - 12
            else hour

        val h1 = displayHour / 10
        val h2 = displayHour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        views.setTextViewText(R.id.digit_h1, h1.toString())
        views.setTextViewText(R.id.digit_h2, h2.toString())
        views.setTextViewText(R.id.digit_m1, m1.toString())
        views.setTextViewText(R.id.digit_m2, m2.toString())
        views.setTextViewText(R.id.ampm, if (is24h) "" else if (hour < 12) "AM" else "PM")
    }

    /**
     * Sets the correct digit on ViewFlipper layouts WITHOUT animation.
     * Used when the user disables flip animation but the layout still uses ViewFlippers.
     * Sets the displayed child directly and makes only the active digit visible.
     */
    fun bindStaticClockViews(
        context: Context,
        views: RemoteViews,
        hour: Int,
        minute: Int,
        is24h: Boolean = true
    ) {
        val displayHour = if (is24h) hour
            else if (hour == 0) 12
            else if (hour > 12) hour - 12
            else hour

        val h1 = displayHour / 10
        val h2 = displayHour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        // Use visibility to show correct digit — no setDisplayedChild, no animation
        setDigitVisibility(context, views, "digit_h1", h1)
        setDigitVisibility(context, views, "digit_h2", h2)
        setDigitVisibility(context, views, "digit_m1", m1)
        setDigitVisibility(context, views, "digit_m2", m2)
        views.setTextViewText(R.id.ampm, if (is24h) "" else if (hour < 12) "AM" else "PM")
    }

    fun bindClockViews(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        hour: Int,
        minute: Int,
        is24h: Boolean = true,
        isIncremental: Boolean = false,
        prevDigits: DigitState? = null
    ) {
        val displayHour = if (is24h) hour
            else if (hour == 0) 12
            else if (hour > 12) hour - 12
            else hour

        val h1 = displayHour / 10
        val h2 = displayHour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        android.util.Log.d("WidgetDataBinder", "Binding clock: $hour:$minute (24h=$is24h, incremental=$isIncremental) -> digits $h1$h2:$m1$m2")

        if (isIncremental) {
            // Incremental: use stored previous digits for accurate diff.
            // Falls back to arithmetic "minute - 1" when no stored state is available
            // (e.g. first tick after install or after a widget clear).
            val prev = prevDigits ?: run {
                val pMin = if (minute == 0) 59 else minute - 1
                val pHour = if (minute == 0) { if (hour == 0) 23 else hour - 1 } else hour
                val pDisplay = if (is24h) pHour
                    else if (pHour == 0) 12
                    else if (pHour > 12) pHour - 12
                    else pHour
                DigitState(pDisplay / 10, pDisplay % 10, pMin / 10, pMin % 10)
            }

            if (h1 != prev.h1) {
                android.util.Log.d("WidgetDataBinder", "Incremental flip h1: ${prev.h1} -> $h1")
                views.setDisplayedChild(R.id.digit_h1, h1)
                setDigitVisibility(context, views, "digit_h1", h1)
            }
            if (h2 != prev.h2) {
                android.util.Log.d("WidgetDataBinder", "Incremental flip h2: ${prev.h2} -> $h2")
                views.setDisplayedChild(R.id.digit_h2, h2)
                setDigitVisibility(context, views, "digit_h2", h2)
            }
            if (m1 != prev.m1) {
                android.util.Log.d("WidgetDataBinder", "Incremental flip m1: ${prev.m1} -> $m1")
                views.setDisplayedChild(R.id.digit_m1, m1)
                setDigitVisibility(context, views, "digit_m1", m1)
            }
            if (m2 != prev.m2) {
                android.util.Log.d("WidgetDataBinder", "Incremental flip m2: ${prev.m2} -> $m2")
                views.setDisplayedChild(R.id.digit_m2, m2)
                setDigitVisibility(context, views, "digit_m2", m2)
            }

            val ampmText = if (is24h) "" else if (hour < 12) "AM" else "PM"
            val prevAmpmText = if (is24h) "" else if (
                (if (minute == 0) { if (hour == 0) 23 else hour - 1 } else hour) < 12
            ) "AM" else "PM"
            if (ampmText != prevAmpmText) views.setTextViewText(R.id.ampm, ampmText)
        } else {
            // Full refresh: use visibility only (no setDisplayedChild) to avoid flicker
            setDigitVisibility(context, views, "digit_h1", h1)
            setDigitVisibility(context, views, "digit_h2", h2)
            setDigitVisibility(context, views, "digit_m1", m1)
            setDigitVisibility(context, views, "digit_m2", m2)
            views.setTextViewText(R.id.ampm, if (is24h) "" else if (hour < 12) "AM" else "PM")
        }
    }

    private fun setDigitVisibility(
        context: Context,
        views: RemoteViews,
        prefix: String,
        value: Int
    ) {
        val packageName = context.packageName
        val resources = context.resources
        for (i in 0..9) {
            val childId = resources.getIdentifier("${prefix}_$i", "id", packageName)
            if (childId != 0) {
                views.setViewVisibility(childId, if (i == value) android.view.View.VISIBLE else android.view.View.GONE)
            }
        }
    }

    fun bindWeatherViews(
        context: Context,
        views: RemoteViews,
        weatherData: WeatherData,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
    ) {
        val current = weatherData.currentWeather
        val location = weatherData.location
        val todayForecast = weatherData.dailyForecasts.firstOrNull()

        views.setTextViewText(R.id.city_name, location.name)
        views.setTextViewText(R.id.condition_text, context.getString(current.weatherCondition.labelResId))
        views.setImageViewResource(
            R.id.weather_icon,
            WeatherIconMapper.getDrawableResId(current.weatherCondition)
        )
        views.setTextViewText(
            R.id.current_temp,
            TemperatureFormatter.formatWithUnit(current.temperature, temperatureUnit)
        )
        todayForecast?.let { forecast ->
            val tempFormat = if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit
            views.setTextViewText(
                R.id.high_low,
                context.getString(tempFormat, forecast.temperatureMax) + "/" + context.getString(tempFormat, forecast.temperatureMin)
            )
        }
        
        // Ensure the card is visible once data is bound
        views.setViewVisibility(R.id.weather_card, android.view.View.VISIBLE)
    }


    fun bindWeeklyForecastRows(
        context: Context,
        views: RemoteViews,
        weatherData: WeatherData,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
    ) {
        data class RowIds(val name: Int, val icon: Int, val high: Int)
        val rows = listOf(
            RowIds(R.id.fday1_name, R.id.fday1_icon, R.id.fday1_high),
            RowIds(R.id.fday2_name, R.id.fday2_icon, R.id.fday2_high),
            RowIds(R.id.fday3_name, R.id.fday3_icon, R.id.fday3_high),
            RowIds(R.id.fday4_name, R.id.fday4_icon, R.id.fday4_high),
            RowIds(R.id.fday5_name, R.id.fday5_icon, R.id.fday5_high),
            RowIds(R.id.fday6_name, R.id.fday6_icon, R.id.fday6_high),
            RowIds(R.id.fday7_name, R.id.fday7_icon, R.id.fday7_high),
        )
        // Start from today (index 0) — show 7 days including today
        val futureDays = weatherData.dailyForecasts.take(7)
        futureDays.forEachIndexed { i, f ->
            val r = rows[i]
            val dayLabel = if (i == 0) context.getString(R.string.label_today) else DateFormatter.formatDayName(f.date)
            views.setTextViewText(r.name, dayLabel)
            views.setImageViewResource(r.icon, WeatherIconMapper.getDrawableResId(f.weatherCondition))
            val tempFormat = if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit
            views.setTextViewText(r.high, context.getString(tempFormat, f.temperatureMax))
        }
        views.setViewVisibility(R.id.forecast_container, android.view.View.VISIBLE)
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
