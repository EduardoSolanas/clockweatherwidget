package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.widget.RemoteViews
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.repository.LocationRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration-level contract tests for the partial-vs-full widget update mechanism.
 *
 * Scenario matrix:
 * ┌──────────────────────────┬──────────────────────┬────────────────────────┐
 * │ Scenario                 │ Expected API         │ Digits touched         │
 * ├──────────────────────────┼──────────────────────┼────────────────────────┤
 * │ Widget first placed      │ updateAppWidget      │ All 4 (establish base) │
 * │ Unlock (USER_PRESENT)    │ partiallyUpdate      │ Only changed           │
 * │ Weather refresh          │ partiallyUpdate      │ Only changed           │
 * │ Settings change          │ updateAppWidget      │ All 4 (theme changed)  │
 * │ Process restart          │ updateAppWidget      │ All 4 (state cleared)  │
 * │ Return from detail page  │ pushClockInstant     │ Only changed           │
 * └──────────────────────────┴──────────────────────┴────────────────────────┘
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PartialVsFullUpdateContractTest {

    private val realContext: Context = RuntimeEnvironment.getApplication()
    private lateinit var mockContext: Context
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var entryPoint: WidgetEntryPoint
    private lateinit var updater: ConcreteTestUpdater

    private val widgetId = 50

    private val prefs: Preferences = preferencesOf(
        booleanPreferencesKey("use_24h_clock") to true,
        booleanPreferencesKey("show_date_in_widget") to false,
        booleanPreferencesKey("flip_animation_enabled") to true,
        stringPreferencesKey("temperature_unit") to "CELSIUS",
        stringPreferencesKey("clock_theme") to "dark",
        stringPreferencesKey("clock_tile_size") to "MEDIUM"
    )

    class ConcreteTestUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint
    ) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {
        override val layoutResId = R.layout.widget_compact
        override val rootViewId = R.id.widget_root
        override val dateViewId = R.id.widget_date
        override val usesSimpleClockDigits = false
        override fun bindExtra(
            views: RemoteViews,
            weather: WeatherData,
            tempUnit: TemperatureUnit,
            prefs: Preferences
        ) { }
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        appWidgetManager = mockk()
        every { appWidgetManager.updateAppWidget(any<Int>(), any()) } just Runs
        every { appWidgetManager.partiallyUpdateAppWidget(any<Int>(), any()) } just Runs

        entryPoint = mockk(relaxed = true)

        val dataStore: DataStore<Preferences> = mockk()
        every { entryPoint.dataStore() } returns dataStore
        every { dataStore.data } returns flowOf(prefs)

        val locationRepo: LocationRepository = mockk()
        every { entryPoint.locationRepository() } returns locationRepo
        every { locationRepo.getSavedLocations() } returns flowOf(emptyList())
        coEvery { locationRepo.getCurrentLocation() } returns null

        // Mock context + resources to avoid Robolectric resource resolution failures
        val mockResources: Resources = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns realContext.packageName
        every { mockContext.applicationContext } returns realContext
        every { mockContext.resources } returns mockResources
        every { mockResources.getDimension(any()) } returns 48f
        every { mockResources.getResourceEntryName(any()) } returns "digit_h1"
        every { mockResources.getIdentifier(any(), any(), any()) } returns 12345

        // Mock RemoteViews — Robolectric can't inflate widget layouts
        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setCharSequence(any<Int>(), any<String>(), any<CharSequence>()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewTextSize(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewLayoutHeight(any(), any(), any()) } just Runs

        mockkStatic(android.app.PendingIntent::class)
        every { android.app.PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()

        WidgetClockStateStore.clearWidget(realContext, widgetId)
        updater = ConcreteTestUpdater(mockContext, appWidgetManager, entryPoint)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `scenario - widget first placed uses full updateAppWidget`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `scenario - unlock after prior render uses partiallyUpdateAppWidget`() = runBlocking {
        // Simulate a completed prior render: baseline flag AND digit state both set.
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(14, 37, true))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `scenario - weather refresh with same digits uses partial`() = runBlocking {
        // Simulate a completed prior render.
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        val now = java.time.LocalTime.now()
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(now.hour, now.minute, true))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    /**
     * Regression guard for the "0000 flash" bug.
     *
     * When settings change (theme / tile size), [ClockWeatherApplication.invalidateAllWidgetBaselines]
     * calls [WidgetClockStateStore.clearDigits] — NOT [WidgetClockStateStore.clearWidget].
     * clearDigits preserves [WidgetClockStateStore.isBaselineReady], so the next updateWidget
     * must use [partiallyUpdateAppWidget], never [updateAppWidget] (which resets the layout
     * to XML defaults and briefly shows "0000").
     */
    @Test
    fun `scenario - settings change with active baseline uses partiallyUpdateAppWidget - no 0000 flash`() = runBlocking {
        // Establish baseline (widget has been fully rendered before).
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        // Settings change: clearDigits — does NOT touch baseline.
        WidgetClockStateStore.clearDigits(realContext, widgetId)

        updater.updateWidget(widgetId)

        // MUST be partial — calling updateAppWidget here would flash "0000".
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    /**
     * Edge case: widget was never fully rendered (no baseline) but digit state existed and
     * was then cleared (e.g., data migration). Full update is correct here.
     */
    @Test
    fun `scenario - no baseline plus cleared digits uses full updateAppWidget`() = runBlocking {
        // Digits written (e.g., by a pushClockInstant) but baseline never set.
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.clearDigits(realContext, widgetId)
        // isBaselineReady = false (never called markBaselineReady)

        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `scenario - process restart clears all state then uses full updateAppWidget`() = runBlocking {
        WidgetClockStateStore.clearWidget(realContext, widgetId)

        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `scenario - clearWidget after active baseline resets to full update`() = runBlocking {
        // Establish baseline first.
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)

        // Full state wipe (widget removed + re-added, or process restart).
        WidgetClockStateStore.clearWidget(realContext, widgetId)

        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `scenario - full lifecycle transitions correctly between full and partial`() = runBlocking {
        // 1. Widget placed → full
        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        clearMocks(appWidgetManager, answers = false)

        // 2. Normal refresh → partial
        updater.updateWidget(widgetId)
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        clearMocks(appWidgetManager, answers = false)

        // 3. Settings change: clearDigits preserves baseline → PARTIAL (no 0000 flash).
        WidgetClockStateStore.clearDigits(realContext, widgetId)
        updater.updateWidget(widgetId)
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        clearMocks(appWidgetManager, answers = false)

        // 4. Back to normal → partial again
        updater.updateWidget(widgetId)
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `updateWidget calls exactly one API per invocation`() = runBlocking {
        // First render → exactly one full call
        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        clearMocks(appWidgetManager, answers = false)

        // Subsequent render → exactly one partial call
        updater.updateWidget(widgetId)
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }
}
