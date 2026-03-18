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
 * On every resume event (screen-on, unlock, dreaming-stopped) an instant sync is
 * performed so the widget shows the correct time immediately, without animation.
 *
 * Must be registered via [Context.registerReceiver] — screen intents are not deliverable
 * to manifest-declared receivers since API 26.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF — unregister TIME_TICK + cancel alarm")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.cancelNextTick(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON — register TIME_TICK + instant sync + schedule alarm backup")
                app.registerTimeTickReceiver()
                launchSync(app, context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (unlocked) — instant sync")
                launchSync(app, context)
            }
            Intent.ACTION_DREAMING_STARTED -> {
                Log.d(TAG, "Dreaming started — unregister TIME_TICK + cancel alarm")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.cancelNextTick(context)
            }
            Intent.ACTION_DREAMING_STOPPED -> {
                Log.d(TAG, "Dreaming stopped — register TIME_TICK + instant sync + schedule alarm backup")
                app.registerTimeTickReceiver()
                launchSync(app, context)
            }
        }
    }

    private fun launchSync(app: ClockWeatherApplication, context: Context) {
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
