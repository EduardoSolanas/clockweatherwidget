package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
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
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ClockWeatherApplication ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF - unregister TIME_TICK + keepalive")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.scheduleKeepalive(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON - register TIME_TICK + unlock convergence")
                app.registerTimeTickReceiver()
                launchUnlockConvergence(app, context, Intent.ACTION_SCREEN_ON)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present - unlock convergence")
                launchUnlockConvergence(app, context, Intent.ACTION_USER_PRESENT)
            }
            Intent.ACTION_DREAMING_STARTED -> {
                Log.d(TAG, "Dreaming started - unregister TIME_TICK + keepalive")
                app.unregisterTimeTickReceiver()
                ClockAlarmReceiver.scheduleKeepalive(context)
            }
            Intent.ACTION_DREAMING_STOPPED -> {
                Log.d(TAG, "Dreaming stopped - register TIME_TICK + unlock convergence")
                app.registerTimeTickReceiver()
                launchUnlockConvergence(app, context, Intent.ACTION_DREAMING_STOPPED)
            }
            Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                val reason = intent.getStringExtra("reason")
                if (reason == "homekey") {
                    Log.d(TAG, "Home key detected - quiet home convergence")
                    launchUnlockConvergence(app, context, Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                }
            }
        }
    }

    private fun launchUnlockConvergence(
        app: ClockWeatherApplication,
        context: Context,
        sourceAction: String
    ) {
        val now = SystemClock.elapsedRealtime()
        val throttleEnabled = sourceAction != Intent.ACTION_USER_PRESENT
        if (throttleEnabled &&
            lastUnlockConvergenceMs > 0L &&
            now - lastUnlockConvergenceMs < UNLOCK_CONVERGENCE_THROTTLE_MS
        ) {
            Log.d(TAG, "Unlock convergence throttled")
            return
        }
        lastUnlockConvergenceMs = now

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(12_000) {
                    // All unlock/screen transitions are quiet no-animation sync paths.
                    app.syncClockNow(
                        context,
                        suppressAnimation = true
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
        @Volatile private var lastUnlockConvergenceMs: Long = 0L
        private const val UNLOCK_CONVERGENCE_THROTTLE_MS = 2_500L
        internal fun resetUnlockConvergenceThrottleForTests() {
            lastUnlockConvergenceMs = 0L
        }
        @Suppress("DEPRECATION")
        fun buildIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        }
    }
}
