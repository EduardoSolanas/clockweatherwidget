package com.clockweather.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Calendar

/**
 * High-reliability clock tick receiver using AlarmManager.
 * This is the primary driver for off-process widget updates.
 */
class ClockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var reschedule = true
            try {
                withTimeout(10_000) {
                    if (!hasAnyActiveWidgets(context)) {
                        cancelNextTick(context)
                        reschedule = false
                        return@withTimeout
                    }
                    app.refreshAllWidgets(context, isClockTick = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh timed out or failed; rescheduling anyway", e)
            } finally {
                if (reschedule) {
                    val isHighPrecision = app.resolveHighPrecision()
                    scheduleNextTick(context, isHighPrecision)
                }
                pendingResult.finish()
            }
        }
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

        fun scheduleNextTick(context: Context, isHighPrecision: Boolean = true) {
            if (!hasAnyActiveWidgets(context)) {
                cancelNextTick(context)
                return
            }

            val batteryPct = getBatteryPercent(context)
            if (batteryPct <= 5) {
                Log.d(TAG, "Battery critical ($batteryPct %) — skipping alarm scheduling")
                cancelNextTick(context)
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ClockAlarmReceiver::class.java).apply { action = ACTION_ALARM_TICK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 1)
            }

            // Always use RTC_WAKEUP for the minute tick to ensure reliability
            val alarmType = AlarmManager.RTC_WAKEUP

            if (isHighPrecision && batteryPct > 15) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(alarmType, calendar.timeInMillis, pendingIntent)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(alarmType, calendar.timeInMillis, pendingIntent)
                }
            }
            Log.d(TAG, "Scheduled next tick for ${calendar.time} (precision=$isHighPrecision)")
        }

        fun hasAnyActiveWidgets(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            return widgetProviders.any { providerClass ->
                appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass)).isNotEmpty()
            }
        }

        fun cancelNextTick(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ClockAlarmReceiver::class.java).apply { action = ACTION_ALARM_TICK },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Alarm cancelled")
        }

        private fun getBatteryPercent(context: Context): Int {
            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            return if (scale > 0 && level >= 0) (level * 100) / scale else 100
        }
    }
}
