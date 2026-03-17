package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler

class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                // Trigger immediate widget refresh
                WidgetUpdateScheduler.sendUpdateBroadcast(context)
            }
        }
    }
}

