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
import android.os.PowerManager
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
 *
 * Battery-aware scheduling tiers:
 * - Battery > 15 %  → honour user's high-precision setting
 * - Battery 6–15 %  → force non-wakeup + inexact
 * - Battery ≤ 5 %   → cancel alarm entirely (screen-on receiver will resume)
 */
class ClockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(10_000) {
                    if (!hasAnyActiveWidgets(context)) {
                        cancelNextTick(context)
                        return@withTimeout
                    }
                    app.refreshAllWidgets(context, isClockTick = true)
                    val isHighPrecision = app.resolveHighPrecision()
                    scheduleNextTick(context, isHighPrecision)
                }
            } finally {
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

        // ── Scheduling ─────────────────────────────────────────────────

        /**
         * Schedules the next alarm at the start of the next minute.
         *
         * @param isHighPrecision  When `true` (default), uses exact wakeup alarms.
         *                         When `false`, uses non-wakeup inexact alarms.
         *                         Callers with DataStore access should resolve
         *                         [com.clockweather.app.presentation.settings.SettingsViewModel.KEY_HIGH_PRECISION].
         */
        fun scheduleNextTick(context: Context, isHighPrecision: Boolean = true) {
            if (!hasAnyActiveWidgets(context)) {
                cancelNextTick(context)
                return
            }

            // ── Battery awareness ───────────────────────────────────
            val batteryPct = getBatteryPercent(context)
            val effectiveHighPrecision: Boolean
            val useWakeup: Boolean

            when {
                batteryPct in 0..5 -> {
                    // Critical battery — don't schedule at all.
                    // The ScreenStateReceiver will resume when the screen turns on.
                    Log.d(TAG, "Battery critical ($batteryPct %) — skipping alarm scheduling")
                    cancelNextTick(context)
                    return
                }
                batteryPct in 6..15 -> {
                    // Low battery — force battery-friendly mode regardless of setting
                    Log.d(TAG, "Battery low ($batteryPct %) — forcing non-wakeup inexact")
                    effectiveHighPrecision = false
                    useWakeup = false
                }
                else -> {
                    // Normal battery — respect user setting
                    effectiveHighPrecision = isHighPrecision
                    useWakeup = isHighPrecision
                }
            }

            // ── Screen check ────────────────────────────────────────
            // If screen is off, don't bother scheduling a wakeup alarm.
            // The ScreenStateReceiver will reschedule when the screen turns on.
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val screenOn = pm.isInteractive
            if (!screenOn && useWakeup) {
                Log.d(TAG, "Screen off — skipping wakeup alarm (ScreenStateReceiver will resume)")
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

            val alarmType = if (useWakeup) AlarmManager.RTC_WAKEUP else AlarmManager.RTC

            if (effectiveHighPrecision) {
                // Exact scheduling
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        Log.d(TAG, "High-Precision: EXACT alarm (type=$alarmType)")
                        alarmManager.setExactAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                    } else {
                        Log.d(TAG, "High-Precision: permission missing → inexact allowWhileIdle")
                        alarmManager.setAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "High-Precision: EXACT allowWhileIdle (type=$alarmType)")
                    alarmManager.setExactAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(alarmType, calendar.timeInMillis, pendingIntent)
                }
            } else {
                // Battery-friendly inexact scheduling
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "Battery-Friendly: inexact allowWhileIdle (type=$alarmType)")
                    alarmManager.setAndAllowWhileIdle(alarmType, calendar.timeInMillis, pendingIntent)
                } else {
                    Log.d(TAG, "Battery-Friendly: standard alarm (type=$alarmType)")
                    alarmManager.set(alarmType, calendar.timeInMillis, pendingIntent)
                }
            }
        }

        // ── Helpers ─────────────────────────────────────────────────

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

        /**
         * Returns the current battery percentage (0–100), or 100 if unknown.
         * Uses a sticky broadcast — no registration needed.
         */
        private fun getBatteryPercent(context: Context): Int {
            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            return if (scale > 0 && level >= 0) (level * 100) / scale else 100
        }
    }
}
