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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.clockweather.app.util.dataStore
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidApp
class ClockWeatherApplication : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(Dispatchers.Default)
    private val widgetRefreshMutex = Mutex()

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
    fun pushClockInstant(
        forceAllDigits: Boolean = false,
        suppressAnimationWindow: Boolean = false
    ) {
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
                val views = RemoteViews(packageName, layoutId)

                val ampm = if (is24h) "" else if (now.hour < 12) "AM" else "PM"
                var hasChanges = false

                if (forceAllDigits || prev == null) {
                    // When suppression is requested (weather->home / unlock convergence),
                    // avoid setDisplayedChild to prevent visible flip transitions.
                    if (!suppressAnimationWindow) {
                        // Align ViewFlipper internal state so next incremental showNext()
                        // animates correctly after a forced sync.
                        views.setDisplayedChild(R.id.digit_h1, h1)
                        views.setDisplayedChild(R.id.digit_h2, h2)
                        views.setDisplayedChild(R.id.digit_m1, m1)
                        views.setDisplayedChild(R.id.digit_m2, m2)
                    }
                    applyDigitVisibility(views, H1_DIGIT_IDS, h1)
                    applyDigitVisibility(views, H2_DIGIT_IDS, h2)
                    applyDigitVisibility(views, M1_DIGIT_IDS, m1)
                    applyDigitVisibility(views, M2_DIGIT_IDS, m2)
                    views.setTextViewText(R.id.ampm, ampm)
                    hasChanges = true
                } else {
                    hasChanges = applyDigitDelta(views, H1_DIGIT_IDS, prev.h1, h1) || hasChanges
                    hasChanges = applyDigitDelta(views, H2_DIGIT_IDS, prev.h2, h2) || hasChanges
                    hasChanges = applyDigitDelta(views, M1_DIGIT_IDS, prev.m1, m1) || hasChanges
                    hasChanges = applyDigitDelta(views, M2_DIGIT_IDS, prev.m2, m2) || hasChanges

                    val previousDisplayHour = prev.h1 * 10 + prev.h2
                    val previousAmpm = if (is24h) "" else if (previousDisplayHour < 12) "AM" else "PM"
                    if (ampm != previousAmpm) {
                        views.setTextViewText(R.id.ampm, ampm)
                        hasChanges = true
                    }
                }

                if (hasChanges) {
                    mgr.partiallyUpdateAppWidget(id, views)
                }
                if (suppressAnimationWindow) {
                    // Suppress flip animations for a short window after forced sync
                    // to avoid transition-time race flicker on the next minute tick(s).
                    WidgetClockStateStore.markNoAnimationUntilEpochMinute(this, id, currentEpochMinute + 2L)
                }
                WidgetClockStateStore.saveLastDigits(this, id, DigitState(h1, h2, m1, m2))
                WidgetClockStateStore.markRendered(this, id, currentEpochMinute)
                if (hasChanges) {
                    android.util.Log.d("ClockWeatherApp", "pushClockInstant: widget $id updated (prev=$prev -> $h1$h2:$m1$m2)")
                }
            }
        }
    }

    private fun applyDigitVisibility(views: RemoteViews, digitIds: IntArray, value: Int) {
        digitIds.forEachIndexed { index, childId ->
            views.setViewVisibility(childId, if (index == value) android.view.View.VISIBLE else android.view.View.GONE)
        }
    }

    private fun applyDigitDelta(
        views: RemoteViews,
        digitIds: IntArray,
        previousValue: Int,
        newValue: Int
    ): Boolean {
        if (previousValue == newValue) return false
        views.setViewVisibility(digitIds[previousValue], android.view.View.GONE)
        views.setViewVisibility(digitIds[newValue], android.view.View.VISIBLE)
        return true
    }

    /**
     * Instantly syncs the clock on all widgets (no animation) and restarts
     * the alarm chain. Call this whenever the widget may be showing stale
     * time — screen unlock, return from detail activity, etc.
     */
    suspend fun syncClockNow(
        context: Context,
        suppressAnimation: Boolean = false,
        reassertAfterReschedule: Boolean = true
    ) {
        android.util.Log.d(
            "ClockWeatherApp",
            "syncClockNow: instant refresh + alarm restart " +
                "(suppressAnimation=$suppressAnimation, reassert=$reassertAfterReschedule)"
        )

        if (suppressAnimation) {
            // Transition-safe path (weather->home / lock->home):
            // avoid full widget rebuild to prevent visible old/new layout blending.
            pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true
            )
            val isHighPrecision = resolveHighPrecision()
            ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
            if (reassertAfterReschedule) {
                // Re-assert once more to handle launchers that defer host redraw
                // during activity/screen transitions.
                pushClockInstant(
                    forceAllDigits = true,
                    suppressAnimationWindow = true
                )
            }
            return
        }

        // Push correct digits immediately before the potentially slow full refresh.
        pushClockInstant(forceAllDigits = true)
        refreshAllWidgets(context, isClockTick = false)
        val isHighPrecision = resolveHighPrecision()
        ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
        // Push again after full refresh to eliminate minute-boundary races
        // that can happen while weather/date binding is executing.
        pushClockInstant(
            forceAllDigits = true,
            suppressAnimationWindow = suppressAnimation
        )
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
    suspend fun refreshAllWidgets(
        context: Context,
        isClockTick: Boolean,
        allowAnimation: Boolean = false
    ) {
        widgetRefreshMutex.withLock {
            android.util.Log.d(
                "ClockWeatherApp",
                "Refreshing all widgets. isClockTick=$isClockTick allowAnimation=$allowAnimation"
            )
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
                                updater.updateClockOnly(
                                    appWidgetId = id,
                                    allowAnimation = allowAnimation
                                )
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

    companion object {
        private val H1_DIGIT_IDS = intArrayOf(
            R.id.digit_h1_0, R.id.digit_h1_1, R.id.digit_h1_2, R.id.digit_h1_3, R.id.digit_h1_4,
            R.id.digit_h1_5, R.id.digit_h1_6, R.id.digit_h1_7, R.id.digit_h1_8, R.id.digit_h1_9
        )
        private val H2_DIGIT_IDS = intArrayOf(
            R.id.digit_h2_0, R.id.digit_h2_1, R.id.digit_h2_2, R.id.digit_h2_3, R.id.digit_h2_4,
            R.id.digit_h2_5, R.id.digit_h2_6, R.id.digit_h2_7, R.id.digit_h2_8, R.id.digit_h2_9
        )
        private val M1_DIGIT_IDS = intArrayOf(
            R.id.digit_m1_0, R.id.digit_m1_1, R.id.digit_m1_2, R.id.digit_m1_3, R.id.digit_m1_4,
            R.id.digit_m1_5, R.id.digit_m1_6, R.id.digit_m1_7, R.id.digit_m1_8, R.id.digit_m1_9
        )
        private val M2_DIGIT_IDS = intArrayOf(
            R.id.digit_m2_0, R.id.digit_m2_1, R.id.digit_m2_2, R.id.digit_m2_3, R.id.digit_m2_4,
            R.id.digit_m2_5, R.id.digit_m2_6, R.id.digit_m2_7, R.id.digit_m2_8, R.id.digit_m2_9
        )
    }
}
