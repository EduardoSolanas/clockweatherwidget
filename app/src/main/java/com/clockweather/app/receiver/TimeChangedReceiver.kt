package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication

class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                // Trigger immediate full widget refresh via the Application class helper
                val app = context.applicationContext as? ClockWeatherApplication
                app?.refreshAllWidgets(context, isClockTick = false)
            }
        }
    }
}
