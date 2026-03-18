package com.clockweather.app

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import com.clockweather.app.presentation.widget.common.WidgetClockStateStore
import com.clockweather.app.receiver.ClockAlarmReceiver
import com.clockweather.app.receiver.ScreenStateReceiver
import com.clockweather.app.receiver.TimeTickReceiver
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.util.WidgetPrefsCache
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.clockweather.app.util.dataStore
import javax.inject.Inject

@HiltAndroidApp
class ClockWeatherApplication : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(Dispatchers.Default)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Dynamically registered — must be kept as an instance field to unregister later. */
    private var screenStateReceiver: ScreenStateReceiver? = null

    /** Registered while screen is on to receive the free system minute tick. */
    private var timeTickReceiver: TimeTickReceiver? = null

    override fun onCreate() {
        super.onCreate()
        restoreLanguageSetting()

        // Initialise the in-memory preference cache so minute ticks skip disk I/O
        WidgetPrefsCache.init(dataStore, appScope)

        if (ClockAlarmReceiver.hasAnyActiveWidgets(this)) {
            registerScreenStateReceiver()
            // Screen may already be on when the process starts (e.g. after OEM kill).
            // Register TIME_TICK immediately so we don't miss ticks until the next
            // ACTION_SCREEN_ON event.
            registerTimeTickReceiver()

            appScope.launch {
                resetClockStateForActiveWidgets(this@ClockWeatherApplication)

                // Instant sync + alarm backup
                syncClockNow(this@ClockWeatherApplication)
            }
        }
    }

    // ── Screen state receiver management ────────────────────────────

    fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return // already registered
        val receiver = ScreenStateReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, ScreenStateReceiver.buildIntentFilter(), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, ScreenStateReceiver.buildIntentFilter())
        }
        screenStateReceiver = receiver
        android.util.Log.d("ClockWeatherApp", "ScreenStateReceiver registered")
    }

    fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenStateReceiver = null
            android.util.Log.d("ClockWeatherApp", "ScreenStateReceiver unregistered")
        }
        unregisterTimeTickReceiver()
    }

    // ── TIME_TICK receiver management ────────────────────────────────

    /**
     * Registers for [Intent.ACTION_TIME_TICK] — the free system broadcast that fires
     * every minute while the screen is on. This is the primary tick source.
     * Call on screen-on; unregister on screen-off to avoid leaks.
     */
    fun registerTimeTickReceiver() {
        if (timeTickReceiver != null) return // already registered
        val receiver = TimeTickReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, TimeTickReceiver.buildIntentFilter(), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, TimeTickReceiver.buildIntentFilter())
        }
        timeTickReceiver = receiver
        android.util.Log.d("ClockWeatherApp", "TimeTickReceiver registered")
    }

    fun unregisterTimeTickReceiver() {
        timeTickReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            timeTickReceiver = null
            android.util.Log.d("ClockWeatherApp", "TimeTickReceiver unregistered")
        }
    }

    // ── Instant clock sync ────────────────────────────────────────

    /**
     * Instantly syncs the clock on all widgets (no animation) and restarts
     * the alarm chain. Call this whenever the widget may be showing stale
     * time — screen unlock, return from detail activity, etc.
     */
    suspend fun syncClockNow(context: Context) {
        android.util.Log.d("ClockWeatherApp", "syncClockNow: instant refresh + alarm restart")
        refreshAllWidgets(context, isClockTick = false)
        val isHighPrecision = resolveHighPrecision()
        ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Read the high-precision preference. Defaults to true. */
    suspend fun resolveHighPrecision(): Boolean {
        return try {
            dataStore.data.first()[booleanPreferencesKey("high_precision_clock")] ?: true
        } catch (_: Exception) {
            true
        }
    }

    private fun resetClockStateForActiveWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val providerClasses = listOf(
            CompactWidgetProvider::class.java,
            ExtendedWidgetProvider::class.java,
            ForecastWidgetProvider::class.java,
            LargeWidgetProvider::class.java
        )
        providerClasses.forEach { providerClass ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass))
            ids.forEach { id -> WidgetClockStateStore.clearWidget(context, id) }
        }
    }

    /**
     * Internal helper to refresh all active widgets.
     * @param isClockTick If true, it performs a partial 'incremental' clock update (animations enabled).
     *                    If false, it performs a full widget update.
     */
    suspend fun refreshAllWidgets(context: Context, isClockTick: Boolean) {
        android.util.Log.d("ClockWeatherApp", "Refreshing all widgets. isClockTick=$isClockTick")
        val mgr = AppWidgetManager.getInstance(context)
        try {
            val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            
            val providers = listOf(
                CompactWidgetProvider(),
                ExtendedWidgetProvider(),
                ForecastWidgetProvider(),
                LargeWidgetProvider()
            )

            providers.forEach { provider ->
                val component = ComponentName(context, provider::class.java)
                val ids = mgr.getAppWidgetIds(component)
                android.util.Log.d("ClockWeatherApp", "Checking provider ${component.shortClassName}: found ${ids.size} IDs")
                if (ids.isNotEmpty()) {
                    val updater = provider.getUpdater(context.applicationContext, mgr, entryPoint)
                    ids.forEach { id ->
                        if (isClockTick) {
                            updater.updateClockOnly(id)
                        } else {
                            updater.updateWidget(id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ClockWeatherApp", "Failed to refresh widgets", e)
        }
    }

    private fun restoreLanguageSetting() {
        try {
            // Use the shared delegate accessor (guarantees single instance)
            val languageCode = kotlin.runCatching {
                runBlocking {
                    dataStore.data
                        .map { it[SettingsViewModel.KEY_LANGUAGE] ?: "system" }
                        .first()
                }
            }.getOrDefault("system")

            val locale = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(locale)
        } catch (_: Exception) { }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
