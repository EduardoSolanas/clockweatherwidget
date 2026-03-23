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
    @Volatile
    private var lastObservedTimeTickEpochMinute: Long = -1L

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

    fun isTimeTickReceiverRegistered(): Boolean = timeTickReceiver != null

    fun markTimeTickObserved(epochMinute: Long) {
        lastObservedTimeTickEpochMinute = epochMinute
    }

    fun getLastObservedTimeTickEpochMinute(): Long = lastObservedTimeTickEpochMinute

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
        suppressAnimationWindow: Boolean = false,
        quietRender: Boolean = false,
        alignDisplayedChildrenOnly: Boolean = false
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
        val atomicClockProviders = setOf(CompactWidgetProvider::class.java)

        providerLayouts.forEach { (providerClass, layoutId) ->
            val usesAtomicClockText = providerClass in atomicClockProviders
            val ids = mgr.getAppWidgetIds(ComponentName(this, providerClass))
            ids.forEach { id ->
                if (suppressAnimationWindow) {
                    // Pre-arm suppression before any RemoteViews push so concurrent
                    // minute ticks observe the quiet window immediately.
                    WidgetClockStateStore.markNoAnimationUntilEpochMinute(this, id, currentEpochMinute + 1L)
                }
                val prev = WidgetClockStateStore.getLastDigits(this, id)
                val views = RemoteViews(packageName, layoutId)
                val useQuietRender = quietRender || suppressAnimationWindow
                val changedDigitsCount = if (forceAllDigits || prev == null) {
                    4
                } else {
                    listOf(prev.h1 != h1, prev.h2 != h2, prev.m1 != m1, prev.m2 != m2).count { it }
                }
                val renderPath = when {
                    usesAtomicClockText && forceAllDigits -> "atomic_force_text"
                    usesAtomicClockText && prev == null -> "atomic_baseline_text"
                    usesAtomicClockText -> "atomic_delta_text"
                    forceAllDigits && alignDisplayedChildrenOnly -> "force_align_displayed_children"
                    forceAllDigits && useQuietRender -> "force_full_visibility_no_animation"
                    forceAllDigits -> "force_full_with_displayed_child_alignment"
                    prev == null -> "baseline_full_visibility"
                    else -> "delta_visibility_only"
                }

                val ampm = if (is24h) "" else if (now.hour < 12) "AM" else "PM"
                var hasChanges = false

                if (forceAllDigits || prev == null) {
                    if (usesAtomicClockText) {
                        views.setTextViewText(R.id.digit_h1, h1.toString())
                        views.setTextViewText(R.id.digit_h2, h2.toString())
                        views.setTextViewText(R.id.digit_m1, m1.toString())
                        views.setTextViewText(R.id.digit_m2, m2.toString())
                        views.setTextViewText(R.id.ampm, ampm)
                        hasChanges = true
                    } else if (alignDisplayedChildrenOnly) {
                        // Launcher-host reassert path: align flipper indices only,
                        // avoid visibility fan-out that can look like a full-tile refresh.
                        views.setDisplayedChild(R.id.digit_h1, h1)
                        views.setDisplayedChild(R.id.digit_h2, h2)
                        views.setDisplayedChild(R.id.digit_m1, m1)
                        views.setDisplayedChild(R.id.digit_m2, m2)
                        views.setTextViewText(R.id.ampm, ampm)
                        hasChanges = true
                    } else {
                        // When suppression is requested (weather->home / unlock convergence),
                        // avoid setDisplayedChild to prevent visible flip transitions.
                        if (!useQuietRender) {
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
                    }
                } else {
                    if (usesAtomicClockText) {
                        if (prev.h1 != h1) {
                            views.setTextViewText(R.id.digit_h1, h1.toString())
                            hasChanges = true
                        }
                        if (prev.h2 != h2) {
                            views.setTextViewText(R.id.digit_h2, h2.toString())
                            hasChanges = true
                        }
                        if (prev.m1 != m1) {
                            views.setTextViewText(R.id.digit_m1, m1.toString())
                            hasChanges = true
                        }
                        if (prev.m2 != m2) {
                            views.setTextViewText(R.id.digit_m2, m2.toString())
                            hasChanges = true
                        }
                        val previousDisplayHour = prev.h1 * 10 + prev.h2
                        val previousAmpm = if (is24h) "" else if (previousDisplayHour < 12) "AM" else "PM"
                        if (ampm != previousAmpm) {
                            views.setTextViewText(R.id.ampm, ampm)
                            hasChanges = true
                        }
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
                }

                if (hasChanges) {
                    mgr.partiallyUpdateAppWidget(id, views)
                }
                WidgetClockStateStore.saveLastDigits(this, id, DigitState(h1, h2, m1, m2))
                WidgetClockStateStore.markRendered(this, id, currentEpochMinute)
                if (hasChanges) {
                    android.util.Log.d(
                        "ClockWeatherApp",
                        "CLOCK_TRACE pushClockInstant widget=$id minute=$currentEpochMinute " +
                            "path=$renderPath changedDigits=$changedDigitsCount " +
                            "quietRender=$quietRender suppressWindow=$suppressAnimationWindow " +
                            "forceAll=$forceAllDigits prev=$prev new=$h1$h2:$m1$m2"
                    )
                } else {
                    android.util.Log.d(
                        "ClockWeatherApp",
                        "CLOCK_TRACE pushClockInstant widget=$id minute=$currentEpochMinute " +
                            "path=$renderPath changedDigits=0 no-op prev=$prev new=$h1$h2:$m1$m2"
                    )
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
