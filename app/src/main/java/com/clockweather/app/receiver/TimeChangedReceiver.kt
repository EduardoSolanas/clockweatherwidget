package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        withTimeout(10_000) {
                            val app = context.applicationContext as? ClockWeatherApplication
                            app?.refreshAllWidgets(context, isClockTick = false)
                            val isHighPrecision = app?.resolveHighPrecision() ?: true
                            ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
