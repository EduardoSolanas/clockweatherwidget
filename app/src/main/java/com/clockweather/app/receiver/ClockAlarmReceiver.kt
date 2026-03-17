package com.clockweather.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.clockweather.app.util.dataStore
import java.util.Calendar

/**
 * High-reliability clock tick receiver using AlarmManager.
 * This is the primary driver for off-process widget updates.
 */
class ClockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return
        if (!hasAnyActiveWidgets(context)) {
            cancelNextTick(context)
            return
        }
        
        // Update all widgets with the current time
        // We pass isClockTick = true to trigger flip animations if digits change
        app.refreshAllWidgets(context, isClockTick = true)
        
        // Self-reschedule for the next minute boundary
        scheduleNextTick(context)
    }

    companion object {
        private const val TAG = "ClockAlarmReceiver"
        const val ACTION_ALARM_TICK = "com.clockweather.app.ACTION_ALARM_TICK"
        private val widgetProviders = listOf(
            CompactWidgetProvider::class.java,
            ExtendedWidgetProvider::class.java,
            ForecastWidgetProvider::class.java,
            LargeWidgetProvider::class.java
        )

        /**
         * Schedules the next alarm at the start of the next minute.
         * Falls back to inexact but robust 'allowWhileIdle' if exact permission is not granted.
         */
        fun scheduleNextTick(context: Context) {
            if (!hasAnyActiveWidgets(context)) {
                cancelNextTick(context)
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Read preference
            val isHighPrecision = kotlinx.coroutines.runBlocking {
                val key = androidx.datastore.preferences.core.booleanPreferencesKey("high_precision_clock")
                context.dataStore.data
                    .map { it[key] ?: false }
                    .first()
            }

            val intent = Intent(context, ClockAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TICK
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 1)
            }

            // Scheduling logic
            if (isHighPrecision) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        Log.d(TAG, "High-Precision: Scheduling EXACT alarm")
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    } else {
                        Log.d(TAG, "High-Precision: Permission missing, fallback to inexact allowWhileIdle")
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "High-Precision: Scheduling EXACT allowWhileIdle")
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                // Not high-precision: use system-friendly inexact scheduling
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "Battery-Friendly: Scheduling inexact allowWhileIdle")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    Log.d(TAG, "Battery-Friendly: Scheduling standard alarm")
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            }
        }

        fun hasAnyActiveWidgets(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            return widgetProviders.any { providerClass ->
                appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass)).isNotEmpty()
            }
        }
        
        fun cancelNextTick(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ClockAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            
            alarmManager.cancel(pendingIntent)
        }
    }
}
