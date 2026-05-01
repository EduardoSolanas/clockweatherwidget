package com.clockweather.app.presentation.widget.common

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
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
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED
    ) {
        val current = weatherData.currentWeather
        val location = weatherData.location
        val todayForecast = weatherData.dailyForecasts.firstOrNull()

        views.setTextViewText(R.id.city_name, location.area ?: location.name)
        views.setTextViewText(R.id.condition_text, context.getString(current.weatherCondition.labelResId))
        views.setImageViewResource(
            R.id.weather_icon,
            WeatherIconMapper.getDrawableResId(current.weatherCondition, iconStyle)
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

        views.setViewVisibility(R.id.weather_card, android.view.View.VISIBLE)
    }

    fun bindWeatherUnavailableViews(
        context: Context,
        views: RemoteViews,
        iconStyle: WeatherIconMapper.IconStyle = WeatherIconMapper.IconStyle.GLASS_LAYERED,
    ) {
        views.setTextViewText(R.id.city_name, context.getString(R.string.widget_weather_unavailable_title))
        views.setTextViewText(R.id.condition_text, context.getString(R.string.widget_weather_unavailable_condition))
        views.setImageViewResource(
            R.id.weather_icon,
            WeatherIconMapper.getDrawableResId(com.clockweather.app.domain.model.WeatherCondition.PARTLY_CLOUDY_DAY, iconStyle)
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
                views.setImageViewResource(r.icon, WeatherIconMapper.getDrawableResId(forecast.weatherCondition, iconStyle))
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

internal fun selectForecastWidgetDays(weatherData: WeatherData): List<com.clockweather.app.domain.model.DailyForecast> {
    val forecastAnchorDate = weatherData.currentWeather.lastUpdated.toLocalDate()
    return weatherData.dailyForecasts
        .sortedBy { it.date }
        .filter { it.date.isAfter(forecastAnchorDate) }
        .take(5)
}
