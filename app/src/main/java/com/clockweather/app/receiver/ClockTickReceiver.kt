package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler

class ClockTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdateScheduler.ACTION_CLOCK_TICK) {
            // Re-schedule for the next minute
            WidgetUpdateScheduler.scheduleClockAlarm(context)
            // Trigger widget refresh
            WidgetUpdateScheduler.sendUpdateBroadcast(context)
        }
    }
}
