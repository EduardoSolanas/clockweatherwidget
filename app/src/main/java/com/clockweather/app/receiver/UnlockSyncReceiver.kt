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
 * Manifest-level unlock/screen-on fallback.
 *
 * Keeps widget clock sync resilient if the process was killed and dynamic receivers
 * were not registered at unlock time.
 */
class UnlockSyncReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_USER_PRESENT &&
			intent.action != Intent.ACTION_SCREEN_ON
		) {
			return
		}

		val pendingResult = goAsync()
		CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
			try {
				withTimeout(10_000) {
					if (!ClockAlarmReceiver.hasAnyActiveWidgets(context)) return@withTimeout

					val app = context.applicationContext as? ClockWeatherApplication ?: return@withTimeout
					Log.d(TAG, "Unlock fallback sync for action=${intent.action}")

					app.registerScreenStateReceiver()
					app.registerTimeTickReceiver()
					app.syncClockNow(
						context,
						suppressAnimation = true,
						reassertAfterReschedule = intent.action != Intent.ACTION_USER_PRESENT
					)

					val isHighPrecision = app.resolveHighPrecision()
					ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
				}
			} finally {
				pendingResult.finish()
			}
		}
	}

	companion object {
		private const val TAG = "UnlockSyncReceiver"
	}
}
