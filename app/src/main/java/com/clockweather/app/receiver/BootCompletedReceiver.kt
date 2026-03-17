package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.worker.WeatherUpdateScheduler

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Re-schedule weather updates after reboot
            WeatherUpdateScheduler.schedule(context)
            
            // Re-schedule clock ticks
            ClockAlarmReceiver.scheduleNextTick(context)
        }
    }
}

