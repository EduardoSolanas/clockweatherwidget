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

    fun bindClockViews(views: RemoteViews, hour: Int, minute: Int, is24h: Boolean = true) {
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

        if (is24h) {
            views.setTextViewText(R.id.ampm, "")
        } else {
            views.setTextViewText(R.id.ampm, if (hour < 12) "AM" else "PM")
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
        views.setTextViewText(R.id.date_text, DateFormatter.formatDate(LocalDate.now()))
        views.setTextViewText(R.id.condition_text, current.weatherCondition.description)
        views.setImageViewResource(
            R.id.weather_icon,
            WeatherIconMapper.getDrawableResId(current.weatherCondition)
        )
        views.setTextViewText(
            R.id.current_temp,
            TemperatureFormatter.formatWithUnit(current.temperature, temperatureUnit)
        )
        todayForecast?.let { forecast ->
            views.setTextViewText(
                R.id.high_low,
                "${TemperatureFormatter.format(forecast.temperatureMax, temperatureUnit)}°/${TemperatureFormatter.format(forecast.temperatureMin, temperatureUnit)}°"
            )
        }
    }


    fun bindWeeklyForecastRows(
        context: Context,
        views: RemoteViews,
        weatherData: WeatherData,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
    ) {
        data class RowIds(val name: Int, val icon: Int, val cond: Int, val high: Int, val low: Int)
        val rows = listOf(
            RowIds(R.id.fday1_name, R.id.fday1_icon, R.id.fday1_cond, R.id.fday1_high, R.id.fday1_low),
            RowIds(R.id.fday2_name, R.id.fday2_icon, R.id.fday2_cond, R.id.fday2_high, R.id.fday2_low),
            RowIds(R.id.fday3_name, R.id.fday3_icon, R.id.fday3_cond, R.id.fday3_high, R.id.fday3_low),
            RowIds(R.id.fday4_name, R.id.fday4_icon, R.id.fday4_cond, R.id.fday4_high, R.id.fday4_low),
            RowIds(R.id.fday5_name, R.id.fday5_icon, R.id.fday5_cond, R.id.fday5_high, R.id.fday5_low),
            RowIds(R.id.fday6_name, R.id.fday6_icon, R.id.fday6_cond, R.id.fday6_high, R.id.fday6_low),
            RowIds(R.id.fday7_name, R.id.fday7_icon, R.id.fday7_cond, R.id.fday7_high, R.id.fday7_low),
        )
        // Start from today (index 0) — show 7 days including today
        val futureDays = weatherData.dailyForecasts.take(7)
        futureDays.forEachIndexed { i, f ->
            val r = rows[i]
            val dayLabel = if (i == 0) "Today" else DateFormatter.formatDayName(f.date)
            views.setTextViewText(r.name, dayLabel)
            views.setImageViewResource(r.icon, WeatherIconMapper.getDrawableResId(f.weatherCondition))
            views.setTextViewText(r.cond, f.weatherCondition.description)
            views.setTextViewText(r.high, "${TemperatureFormatter.format(f.temperatureMax, temperatureUnit)}°")
            views.setTextViewText(r.low, "${TemperatureFormatter.format(f.temperatureMin, temperatureUnit)}°")
        }
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
