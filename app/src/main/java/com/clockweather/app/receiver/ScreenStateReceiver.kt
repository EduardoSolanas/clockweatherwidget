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
 * Dynamically registered receiver for screen on/off events.
 *
 * TIME_TICK is used while the screen is on.
 * AlarmManager minute ticks remain as backup for process restarts and screen-off periods.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF - unregister TIME_TICK + keepalive")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.scheduleKeepalive(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON - register TIME_TICK + instant clock push + ensure alarm backup")
                app.registerTimeTickReceiver()
                app.pushClockInstant()
                launchAlarmSchedule(app, context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present - full sync")
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        withTimeout(10_000) {
                            app.syncClockNow(context)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_DREAMING_STARTED -> {
                Log.d(TAG, "Dreaming started - unregister TIME_TICK + keepalive")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.scheduleKeepalive(context)
            }
            Intent.ACTION_DREAMING_STOPPED -> {
                Log.d(TAG, "Dreaming stopped - register TIME_TICK + instant clock push + ensure alarm backup")
                app.registerTimeTickReceiver()
                app.pushClockInstant()
                launchAlarmSchedule(app, context)
            }
        }
    }

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
