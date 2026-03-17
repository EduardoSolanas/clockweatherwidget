package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider

object WidgetUpdateScheduler {

    const val ACTION_CLOCK_TICK = "com.clockweather.app.ACTION_CLOCK_TICK"
    const val ACTION_WEATHER_UPDATE = "com.clockweather.app.ACTION_WEATHER_UPDATE"

    fun sendUpdateBroadcast(context: Context, isClockTick: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val action = if (isClockTick) ACTION_CLOCK_TICK else AppWidgetManager.ACTION_APPWIDGET_UPDATE

        // Update compact widgets
        val compactIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CompactWidgetProvider::class.java)
        )
        if (compactIds.isNotEmpty()) {
            val intent = Intent(context, CompactWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, compactIds)
            }
            context.sendBroadcast(intent)
        }

        // Update extended widgets
        val extendedIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ExtendedWidgetProvider::class.java)
        )
        if (extendedIds.isNotEmpty()) {
            val intent = Intent(context, ExtendedWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, extendedIds)
            }
            context.sendBroadcast(intent)
        }

        // Update forecast widgets
        val forecastIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider::class.java)
        )
        if (forecastIds.isNotEmpty()) {
            val intent = Intent(context, com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, forecastIds)
            }
            context.sendBroadcast(intent)
        }

        // Update large widgets
        val largeIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, LargeWidgetProvider::class.java)
        )
        if (largeIds.isNotEmpty()) {
            val intent = Intent(context, LargeWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, largeIds)
            }
            context.sendBroadcast(intent)
        }
    }
}

