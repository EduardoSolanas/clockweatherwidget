package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.util.TypedValue
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
 * Tests for [BaseWidgetUpdater] partial vs full update logic.
 *
 * Core contracts verified:
 * 1. First render (no stored digits) → uses [updateAppWidget] (full replacement)
 * 2. Subsequent render (digits exist) → uses [partiallyUpdateAppWidget] (merge, no flicker)
 * 3. After [WidgetClockStateStore.clearDigits], next render is treated as first render
 * 4. [updateClockOnly] always uses [partiallyUpdateAppWidget]
 * 5. Epoch-minute dedup in [updateClockOnly] prevents double renders
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BaseWidgetUpdaterTest {

    private val realContext: Context = RuntimeEnvironment.getApplication()
    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var entryPoint: WidgetEntryPoint
    private lateinit var updater: TestWidgetUpdater

    private val widgetId = 99

    private val prefs: Preferences = preferencesOf(
        booleanPreferencesKey("use_24h_clock") to true,
        booleanPreferencesKey("show_date_in_widget") to false,
        booleanPreferencesKey("flip_animation_enabled") to true,
        stringPreferencesKey("temperature_unit") to "CELSIUS",
        stringPreferencesKey("clock_theme") to "dark",
        stringPreferencesKey("clock_tile_size") to "MEDIUM"
    )

    class TestWidgetUpdater(
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

        appWidgetManager = mockk(relaxed = true)
        entryPoint = mockk(relaxed = true)

        val dataStore: DataStore<Preferences> = mockk()
        every { entryPoint.dataStore() } returns dataStore
        every { dataStore.data } returns flowOf(prefs)

        val locationRepo: LocationRepository = mockk()
        every { entryPoint.locationRepository() } returns locationRepo
        every { locationRepo.getSavedLocations() } returns flowOf(emptyList())
        coEvery { locationRepo.getCurrentLocation() } returns null

        // Use a mock context with mock resources so Robolectric's resource resolution
        // doesn't fail on widget-specific IDs (digit_h1_0, etc.)
        mockResources = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns realContext.packageName
        every { mockContext.applicationContext } returns realContext
        every { mockContext.resources } returns mockResources
        every { mockResources.getDimension(any()) } returns 48f
        every { mockResources.getResourceEntryName(any()) } answers { "digit_h1" }
        every { mockResources.getIdentifier(any(), any(), any()) } returns 12345

        // Mock RemoteViews — Robolectric can't inflate widget layouts in unit tests
        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewTextSize(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().showNext(any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewLayoutHeight(any(), any(), any()) } just Runs

        // Mock PendingIntent creation
        mockkStatic(android.app.PendingIntent::class)
        every { android.app.PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()

        // Clear state — use realContext for SharedPreferences (it has real storage)
        WidgetClockStateStore.clearWidget(realContext, widgetId)

        updater = TestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── First render vs subsequent render ────────────────────────

    @Test
    fun `first render uses updateAppWidget for full replacement`() = runBlocking {
        assertNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))

        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    @Test
    fun `subsequent render uses partiallyUpdateAppWidget to avoid flicker`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    @Test
    fun `first render saves digit state for future diffs`() = runBlocking {
        assertNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))

        updater.updateWidget(widgetId)

        assertNotNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))
    }

    @Test
    fun `first render marks epoch minute as rendered`() = runBlocking {
        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId))

        updater.updateWidget(widgetId)

        assertNotNull(WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId))
    }

    // ── clearDigits forces full re-render ──────────────────────

    @Test
    fun `clearDigits causes next updateWidget to use updateAppWidget`() = runBlocking {
        // Establish rendered state
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }

        WidgetClockStateStore.clearDigits(realContext, widgetId)
        clearMocks(appWidgetManager, answers = false)

        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    // ── updateClockOnly ───────────────────────────────────────

    @Test
    fun `updateClockOnly uses partiallyUpdateAppWidget`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        val prevMinute = System.currentTimeMillis() / 60000L - 1
        WidgetClockStateStore.markRendered(realContext, widgetId, prevMinute)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        updater.updateClockOnly(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    @Test
    fun `updateClockOnly dedup skips when same epoch minute already rendered`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        val currentMinute = System.currentTimeMillis() / 60000L
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        updater.updateClockOnly(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    // ── Multiple consecutive calls ──────────────────────────────

    @Test
    fun `two consecutive updateWidget calls use full then partial`() = runBlocking {
        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }

        clearMocks(appWidgetManager, answers = false)

        updater.updateWidget(widgetId)
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    @Test
    fun `no location first render still uses updateAppWidget`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        assertNotNull(WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId))
    }

    @Test
    fun `no location subsequent render uses partiallyUpdateAppWidget`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }
}
