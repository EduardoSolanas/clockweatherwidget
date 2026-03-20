package com.clockweather.app

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.widget.RemoteViews
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
import com.clockweather.app.presentation.widget.common.DigitState
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
import java.time.LocalTime
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
     * Ultra-fast partial clock push — sets only the digits that actually changed
     * via [AppWidgetManager.partiallyUpdateAppWidget]. No DataStore read, no
     * weather fetch, no Hilt — runs in < 10 ms on the calling thread.
     *
     * Safe to call from [android.content.BroadcastReceiver.onReceive] (main thread).
     */
    fun pushClockInstant() {
        val now = LocalTime.now()
        val cachedPrefs = WidgetPrefsCache.getCachedSnapshot()
        val is24h = cachedPrefs?.get(booleanPreferencesKey("use_24h_clock"))
            ?: android.text.format.DateFormat.is24HourFormat(this)
        val mgr = AppWidgetManager.getInstance(this)

        val displayHour = if (is24h) now.hour
            else if (now.hour == 0) 12
            else if (now.hour > 12) now.hour - 12
            else now.hour
        val h1 = displayHour / 10
        val h2 = displayHour % 10
        val m1 = now.minute / 10
        val m2 = now.minute % 10
        val currentEpochMinute = System.currentTimeMillis() / 60000L

        val providerLayouts = mapOf(
            CompactWidgetProvider::class.java to R.layout.widget_compact,
            ExtendedWidgetProvider::class.java to R.layout.widget_extended,
            ForecastWidgetProvider::class.java to R.layout.widget_forecast,
            LargeWidgetProvider::class.java to R.layout.widget_large
        )

        providerLayouts.forEach { (providerClass, layoutId) ->
            val ids = mgr.getAppWidgetIds(ComponentName(this, providerClass))
            ids.forEach { id ->
                val prev = WidgetClockStateStore.getLastDigits(this, id)

                // Already showing the correct time — skip entirely
                if (prev != null && prev.h1 == h1 && prev.h2 == h2 &&
                    prev.m1 == m1 && prev.m2 == m2) {
                    return@forEach
                }

                val views = RemoteViews(packageName, layoutId)

                // Only touch digits that actually changed
                if (prev == null || prev.h1 != h1) views.setDisplayedChild(R.id.digit_h1, h1)
                if (prev == null || prev.h2 != h2) views.setDisplayedChild(R.id.digit_h2, h2)
                if (prev == null || prev.m1 != m1) views.setDisplayedChild(R.id.digit_m1, m1)
                if (prev == null || prev.m2 != m2) views.setDisplayedChild(R.id.digit_m2, m2)
                views.setTextViewText(R.id.ampm, if (is24h) "" else if (now.hour < 12) "AM" else "PM")

                mgr.partiallyUpdateAppWidget(id, views)
                WidgetClockStateStore.saveLastDigits(this, id, DigitState(h1, h2, m1, m2))
                WidgetClockStateStore.markRendered(this, id, currentEpochMinute)
                android.util.Log.d("ClockWeatherApp", "pushClockInstant: widget $id updated (prev=$prev -> $h1$h2:$m1$m2)")
            }
        }
    }

    /**
     * Instantly syncs the clock on all widgets (no animation) and restarts
     * the alarm chain. Call this whenever the widget may be showing stale
     * time — screen unlock, return from detail activity, etc.
     */
    suspend fun syncClockNow(context: Context) {
        android.util.Log.d("ClockWeatherApp", "syncClockNow: instant refresh + alarm restart")

        // Push correct digits immediately before the potentially slow full refresh.
        pushClockInstant()

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

    /**
     * Clears stored digit state for ALL active widgets, forcing the next
     * [updateWidget] call to use [updateAppWidget] (full replacement).
     * Call this when settings change (theme, tile size) so the full
     * layout/styling is pushed fresh.
     */
    fun invalidateAllWidgetBaselines() {
        val mgr = AppWidgetManager.getInstance(this)
        val providerClasses = listOf(
            CompactWidgetProvider::class.java,
            ExtendedWidgetProvider::class.java,
            ForecastWidgetProvider::class.java,
            LargeWidgetProvider::class.java
        )
        providerClasses.forEach { providerClass ->
            val ids = mgr.getAppWidgetIds(ComponentName(this, providerClass))
            ids.forEach { id -> WidgetClockStateStore.clearDigits(this, id) }
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
