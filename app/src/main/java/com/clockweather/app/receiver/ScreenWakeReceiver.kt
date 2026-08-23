package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.worker.WeatherUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Catches the widget up right when the user is about to look at it.
 *
 * Periodic refreshes are suspended while the device sits in Doze, so widget data
 * can drift hours stale overnight. Screen-on/unlock is the earliest signal that
 * the home screen is about to be visible.
 *
 * In order:
 * 1. Redraws widgets immediately from local cache off the main thread so the user
 *    sees immediate updates even while offline or on flaky connectivity.
 *    - USER_PRESENT (unlock): Always redraws immediately.
 *    - SCREEN_ON (notification/ambient): Throttled to minimum 60s intervals to save battery.
 * 2. Enqueues a freshness-gated network refresh via WorkManager (deduplicated via KEEP).
 */
class ScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_USER_PRESENT) {
            return
        }

        val now = System.currentTimeMillis()
        val shouldRedraw = if (action == Intent.ACTION_USER_PRESENT) {
            lastScreenOnRedrawMillis = now
            true
        } else {
            // SCREEN_ON: Throttle to once every 60 seconds
            if (now - lastScreenOnRedrawMillis >= SCREEN_ON_THROTTLE_MS) {
                lastScreenOnRedrawMillis = now
                true
            } else {
                false
            }
        }

        val pendingResult = runCatching { goAsync() }.getOrNull()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (shouldRedraw) {
                    withTimeout(5_000L) {
                        val app = context.applicationContext as? ClockWeatherApplication
                        app?.refreshAllWidgets(context)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    WeatherUpdateScheduler.scheduleImmediateRefresh(context)
                } catch (_: Throwable) {
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    companion object {
        const val SCREEN_ON_THROTTLE_MS = 60_000L
        @Volatile
        var lastScreenOnRedrawMillis = 0L

        fun intentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }
}
