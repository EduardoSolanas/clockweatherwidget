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
 * 2. Enqueues a freshness-gated network refresh via WorkManager (deduplicated via KEEP).
 */
class ScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
            val pendingResult = runCatching { goAsync() }.getOrNull()
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    withTimeout(5_000L) {
                        val app = context.applicationContext as? ClockWeatherApplication
                        app?.refreshAllWidgets(context)
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
    }

    companion object {
        fun intentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }
}
