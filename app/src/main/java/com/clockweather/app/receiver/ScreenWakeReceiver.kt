package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.clockweather.app.worker.WeatherUpdateScheduler

/**
 * Catches the widget up right when the user is about to look at it.
 *
 * Periodic refreshes are suspended while the device sits in Doze, so widget data
 * can drift hours stale overnight. Screen-on/unlock is the earliest signal that
 * the home screen is about to be visible; the enqueued refresh is freshness-gated
 * (and deduplicated via KEEP), so repeated screen-ons with fresh data cost nothing.
 *
 * SCREEN_ON and USER_PRESENT can only be received by a runtime-registered receiver,
 * so registration lives in Application.onCreate and lasts as long as the process.
 */
class ScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
            WeatherUpdateScheduler.scheduleImmediateRefresh(context)
        }
    }

    companion object {
        fun intentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }
}
