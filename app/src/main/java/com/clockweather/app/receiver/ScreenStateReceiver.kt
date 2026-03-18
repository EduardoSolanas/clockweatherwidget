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
 * - Screen OFF -> cancels the minute-tick alarm (CPU never woken for invisible updates).
 * - Screen ON  -> re-schedules the alarm respecting the user's high-precision pref and
 *                performs an immediate full refresh to catch up.
 *
 * Must be registered via [Context.registerReceiver] — screen intents are not deliverable
 * to manifest-declared receivers since API 26.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF — cancelling clock alarm to save battery")
                ClockAlarmReceiver.cancelNextTick(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON — resuming clock alarm + full widget refresh")
                val app = context.applicationContext as? ClockWeatherApplication ?: return
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        withTimeout(12_000) {
                            // B1: resolve user preference instead of hardcoding true
                            val isHighPrecision = app.resolveHighPrecision()
                            ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
                            app.refreshAllWidgets(context, isClockTick = false)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_DREAMING_STARTED -> {
                Log.d(TAG, "Dreaming started (AOD/screensaver) — keeping clock alarm active if battery allows")
                // No longer cancel here; allow ClockAlarmReceiver's battery logic to handle it
            }
            Intent.ACTION_DREAMING_STOPPED -> {
                Log.d(TAG, "Dreaming stopped — resuming clock alarm")
                val app = context.applicationContext as? ClockWeatherApplication ?: return
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        withTimeout(12_000) {
                            val isHighPrecision = app.resolveHighPrecision()
                            ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
                            app.refreshAllWidgets(context, isClockTick = false)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
    companion object {
        private const val TAG = "ScreenStateReceiver"
        fun buildIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
    }
}
