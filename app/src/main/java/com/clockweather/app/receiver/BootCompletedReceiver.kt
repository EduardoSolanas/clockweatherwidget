package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.worker.WeatherUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    WeatherUpdateScheduler.schedule(context)

                    // Re-register the dynamic screen state receiver after reboot
                    val app = context.applicationContext as? ClockWeatherApplication
                    if (ClockAlarmReceiver.hasAnyActiveWidgets(context)) {
                        app?.registerScreenStateReceiver()
                    }

                    val isHighPrecision = app?.resolveHighPrecision() ?: true
                    ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
