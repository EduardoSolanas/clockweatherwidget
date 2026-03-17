package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dynamically-registered receiver for screen on/off events.
 *
 * - Screen OFF → cancels the minute-tick alarm so the CPU is never woken for invisible updates.
 * - Screen ON  → re-schedules the alarm and performs an immediate full refresh to catch up.
 *
 * Must be registered via [Context.registerReceiver] — screen intents are not
 * deliverable to manifest-declared receivers since API 26.
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
                ClockAlarmReceiver.scheduleNextTick(context)

                val app = context.applicationContext as? ClockWeatherApplication ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        // Full refresh so the time + weather are correct immediately
                        app.refreshAllWidgets(context, isClockTick = false)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            // Ambient / screensaver (AOD) handling
            Intent.ACTION_DREAMING_STARTED -> {
                Log.d(TAG, "Dreaming started (AOD/screensaver) — cancelling clock alarm")
                ClockAlarmReceiver.cancelNextTick(context)
            }

            Intent.ACTION_DREAMING_STOPPED -> {
                Log.d(TAG, "Dreaming stopped — resuming clock alarm")
                ClockAlarmReceiver.scheduleNextTick(context)

                val app = context.applicationContext as? ClockWeatherApplication ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        app.refreshAllWidgets(context, isClockTick = false)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"

        /** Build the IntentFilter for dynamic registration. */
        fun buildIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
    }
}

