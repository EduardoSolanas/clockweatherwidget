package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Restores active widgets after the app package is replaced during reinstall/update.
 *
 * Launchers immediately re-host the widget's initial layout, which shows XML defaults
 * until we push real data again. This receiver forces a full repaint.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(10_000) {
                    val app = context.applicationContext as? ClockWeatherApplication ?: return@withTimeout
                    Log.d(TAG, "Package replaced - restoring active widgets")
                    app.refreshAllWidgets(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PackageReplacedReceiver"
    }
}
