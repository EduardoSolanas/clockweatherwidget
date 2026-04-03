package com.clockweather.app

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.clockweather.app.presentation.widget.common.ClockSnapshot
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

        val migratedLegacyClockTheme = runBlocking {
            recoverClockThemeAfterBrokenMigration()
        }

        // Initialise the in-memory preference cache so minute ticks skip disk I/O
        WidgetPrefsCache.init(dataStore, appScope)

        if (ClockAlarmReceiver.hasAnyActiveWidgets(this)) {
            registerScreenStateReceiver()
            // Screen may already be on when the process starts (e.g. after OEM kill).
            // Register TIME_TICK immediately so we don't miss ticks until the next
            // ACTION_SCREEN_ON event.
            registerTimeTickReceiver()

            appScope.launch {
                if (migratedLegacyClockTheme) {
                    invalidateAllWidgetBaselines()
                }
                // Instant sync + alarm backup
                syncClockNow(this@ClockWeatherApplication)
            }
        }
    }

    private suspend fun recoverClockThemeAfterBrokenMigration(): Boolean {
        val brokenMigrationKey = booleanPreferencesKey("clock_theme_mapping_migrated_v1")
        val recoveryMigrationKey = booleanPreferencesKey("clock_theme_mapping_migrated_v2")
        val clockThemeKey = stringPreferencesKey("clock_theme")
        var migrated = false

        dataStore.edit { prefs ->
            if (prefs[recoveryMigrationKey] == true) return@edit

            val currentTheme = prefs[clockThemeKey]
            if (prefs[brokenMigrationKey] == true && currentTheme == SettingsViewModel.CLOCK_THEME_DARK) {
                prefs[clockThemeKey] = SettingsViewModel.CLOCK_THEME_LIGHT
                migrated = true
            } else if (prefs[brokenMigrationKey] != true && currentTheme == SettingsViewModel.CLOCK_THEME_DARK) {
                // Pre-fix installs often persisted "dark" while visually showing the light style.
                // Normalize that legacy value to the intended light theme once.
                prefs[clockThemeKey] = SettingsViewModel.CLOCK_THEME_LIGHT
                migrated = true
            }

            prefs[recoveryMigrationKey] = true
        }

        return migrated
    }

    // ── Screen state receiver management ────────────────────────────

    fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return // already registered
        val receiver = ScreenStateReceiver()
        ContextCompat.registerReceiver(
            this,
            receiver,
            ScreenStateReceiver.buildIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
        ContextCompat.registerReceiver(
            this,
            receiver,
            TimeTickReceiver.buildIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
        val snapshot = ClockSnapshot.now()
        val now = snapshot.localTime
        val currentEpochMinute = snapshot.epochMinute
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

        val providerLayouts = mapOf(
            CompactWidgetProvider::class.java to R.layout.widget_compact,
            ExtendedWidgetProvider::class.java to R.layout.widget_extended,
            ForecastWidgetProvider::class.java to R.layout.widget_forecast,
            LargeWidgetProvider::class.java to R.layout.widget_large
        )
        val simpleTextClockProviders = setOf(
            CompactWidgetProvider::class.java,
            ExtendedWidgetProvider::class.java,
            ForecastWidgetProvider::class.java,
            LargeWidgetProvider::class.java
        )

        providerLayouts.forEach { (providerClass, layoutId) ->
            val usesSimpleClockText = providerClass in simpleTextClockProviders
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
                    usesSimpleClockText && forceAllDigits -> "simple_text_full"
                    usesSimpleClockText && prev == null -> "simple_text_baseline"
                    usesSimpleClockText -> "simple_text_delta"
                    forceAllDigits || prev == null -> "static_visibility_full"
                    else -> "static_visibility_delta"
                }

                val ampm = if (is24h) "" else if (now.hour < 12) "AM" else "PM"
                var hasChanges = false

                if (forceAllDigits || prev == null) {
                    views.setTextViewText(R.id.digit_h1, h1.toString())
                    views.setTextViewText(R.id.digit_h2, h2.toString())
                    views.setTextViewText(R.id.digit_m1, m1.toString())
                    views.setTextViewText(R.id.digit_m2, m2.toString())
                    views.setTextViewText(R.id.ampm, ampm)
                    hasChanges = true
                } else {
                    if (prev.h1 != h1) { views.setTextViewText(R.id.digit_h1, h1.toString()); hasChanges = true }
                    if (prev.h2 != h2) { views.setTextViewText(R.id.digit_h2, h2.toString()); hasChanges = true }
                    if (prev.m1 != m1) { views.setTextViewText(R.id.digit_m1, m1.toString()); hasChanges = true }
                    if (prev.m2 != m2) { views.setTextViewText(R.id.digit_m2, m2.toString()); hasChanges = true }
                    val previousDisplayHour = prev.h1 * 10 + prev.h2
                    val previousAmpm = if (is24h) "" else if (previousDisplayHour < 12) "AM" else "PM"
                    if (ampm != previousAmpm) { views.setTextViewText(R.id.ampm, ampm); hasChanges = true }
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
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true
            )
            val isHighPrecision = resolveHighPrecision()
            ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
            if (reassertAfterReschedule) {
                // Re-assert once more to handle launchers that defer host redraw
                // during activity/screen transitions.
                pushClockInstant(
                    forceAllDigits = false,
                    suppressAnimationWindow = true,
                    quietRender = true
                )
            }
            return
        }

        // Push correct digits immediately before the potentially slow full refresh.
        pushClockInstant(forceAllDigits = false, quietRender = true)
        refreshAllWidgets(context, isClockTick = false)
        val isHighPrecision = resolveHighPrecision()
        ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
        // Push again after full refresh to eliminate minute-boundary races
        // that can happen while weather/date binding is executing.
        pushClockInstant(
            forceAllDigits = false,
            suppressAnimationWindow = suppressAnimation,
            quietRender = true
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

}
