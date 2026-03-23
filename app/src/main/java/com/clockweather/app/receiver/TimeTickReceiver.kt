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
 * Receives [Intent.ACTION_TIME_TICK] — the free, system-sent broadcast that fires
 * every minute while the screen is on.
 *
 * This is the **primary** tick source for clock updates when the screen is on.
 * AlarmManager serves as a backup for when the process is killed and restarted.
 *
 * Must be dynamically registered/unregistered via [ClockWeatherApplication]
 * on screen on/off events. ACTION_TIME_TICK cannot be declared in the manifest.
 */
class TimeTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_TICK) return
        val currentEpochMinute = System.currentTimeMillis() / 60000L
        Log.d(TAG, "TIME_TICK received — incremental clock update minute=$currentEpochMinute")

        val app = context.applicationContext as? ClockWeatherApplication ?: return
        app.markTimeTickObserved(currentEpochMinute)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(10_000) {
                    app.refreshAllWidgets(
                        context,
                        isClockTick = true,
                        allowAnimation = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "TIME_TICK refresh failed", e)
                runCatching {
                    app.pushClockInstant(
                        forceAllDigits = true,
                        suppressAnimationWindow = true
                    )
                }.onFailure { pushError ->
                    Log.w(TAG, "TIME_TICK fallback instant push failed", pushError)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TimeTickReceiver"

        fun buildIntentFilter(): IntentFilter = IntentFilter(Intent.ACTION_TIME_TICK)
    }
}
