package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockweather.app.worker.WeatherUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.clockweather.app.util.dataStore
import androidx.datastore.preferences.core.intPreferencesKey

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    withTimeout(15_000) {
                        // Read stored interval pref so the worker respects the user's setting.
                        val intervalMinutes = try {
                            context.dataStore.data.first()[intPreferencesKey("update_interval_minutes")] ?: 30
                        } catch (_: Exception) { 30 }
                        WeatherUpdateScheduler.schedule(context, intervalMinutes)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
