package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Dynamically-registered receiver for screen on/off events.
 *
 * Orchestrates two tick sources:
 * 1. **TIME_TICK** (primary) — free system broadcast every minute while screen is on.
 *    Registered on screen-on, unregistered on screen-off.
 * 2. **AlarmManager** (backup) — wakes the process if it was killed while screen was on.
 *    Scheduled on screen-on, cancelled on screen-off.
 *
 * On SCREEN_ON an ultra-fast partial clock push is performed synchronously so the
 * correct time is visible within milliseconds — even before the lock screen is dismissed.
 *
 * On USER_PRESENT (unlock) the full sync (weather + date + clock) is launched
 * asynchronously, since the home screen is now visible and weather data is useful.
 *
 * On SCREEN_OFF a low-frequency "keepalive" alarm is scheduled (instead of fully
 * cancelling) so that the process restarts after an OEM kill and re-registers this
 * receiver before the next unlock.
 *
 * Must be registered via [Context.registerReceiver] — screen intents are not deliverable
 * to manifest-declared receivers since API 26.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF — unregister TIME_TICK + schedule keepalive alarm")
                app.unregisterTimeTickReceiver()
                // Keep a slow-cadence alarm alive so the process is restarted after
                // an OEM kill, which re-registers this receiver via Application.onCreate().
                ClockAlarmReceiver.scheduleKeepalive(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON — register TIME_TICK + instant clock push + schedule alarm backup")
                app.registerTimeTickReceiver()
                // Synchronous partial push — only changed digits, < 10 ms.
                app.pushClockInstant()
                // Restart the regular per-minute alarm chain.
                launchAlarmSchedule(app, context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (unlocked) — full sync (clock + weather)")
                // Full async sync — updates weather, date, and pushes a complete widget rebuild.
                launchFullSync(app, context)
            }
            Intent.ACTION_DREAMING_STARTED -> {
                Log.d(TAG, "Dreaming started — unregister TIME_TICK + schedule keepalive alarm")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.scheduleKeepalive(context)
            }
            Intent.ACTION_DREAMING_STOPPED -> {
                Log.d(TAG, "Dreaming stopped — register TIME_TICK + instant clock push + schedule alarm backup")
                app.registerTimeTickReceiver()
                app.pushClockInstant()
                launchAlarmSchedule(app, context)
            }
        }
    }

    /**
     * Launches a full async sync (clock + weather + date).
     * Used on USER_PRESENT when the home screen is visible.
     */
    private fun launchFullSync(app: ClockWeatherApplication, context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(12_000) {
                    app.syncClockNow(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Schedules the regular per-minute alarm backup on a background thread
     * (reads high-precision preference from DataStore).
     */
    private fun launchAlarmSchedule(app: ClockWeatherApplication, context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(5_000) {
                    val isHighPrecision = app.resolveHighPrecision()
                    ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
        fun buildIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
    }
}
