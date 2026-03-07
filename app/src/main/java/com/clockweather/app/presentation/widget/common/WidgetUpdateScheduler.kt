package com.clockweather.app.presentation.widget.common

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import java.util.Calendar

object WidgetUpdateScheduler {

    const val ACTION_CLOCK_TICK = "com.clockweather.app.ACTION_CLOCK_TICK"
    const val ACTION_WEATHER_UPDATE = "com.clockweather.app.ACTION_WEATHER_UPDATE"

    fun scheduleClockAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_CLOCK_TICK).apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel existing alarm
        alarmManager.cancel(pendingIntent)

        // Schedule repeating every minute aligned to the next minute boundary
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15, // 60 seconds
                pendingIntent
            )
        }
    }

    fun cancelClockAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_CLOCK_TICK).apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    fun sendUpdateBroadcast(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update compact widgets
        val compactIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CompactWidgetProvider::class.java)
        )
        if (compactIds.isNotEmpty()) {
            val intent = Intent(context, CompactWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
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
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
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
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, forecastIds)
            }
            context.sendBroadcast(intent)
        }
    }
}

