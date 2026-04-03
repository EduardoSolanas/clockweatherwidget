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
import java.time.LocalTime

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

    class AtomicTestWidgetUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint
    ) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {
        override val layoutResId = R.layout.widget_compact
        override val rootViewId = R.id.widget_root
        override val dateViewId = R.id.widget_date
        override val usesSimpleClockDigits = false
        override val usesAtomicClockText = true

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
        every { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) } just Runs
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
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `subsequent render uses partiallyUpdateAppWidget to avoid flicker`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `first render saves digit state for future diffs`() = runBlocking {
        assertNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))

        updater.updateWidget(widgetId)

        assertNotNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `first render marks epoch minute as rendered`() = runBlocking {
        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId))

        updater.updateWidget(widgetId)

        assertNotNull(WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId))
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `full update marks baseline ready so next tick can animate incrementally`() = runBlocking {
        assertFalse(WidgetClockStateStore.isBaselineReady(realContext, widgetId))

        updater.updateWidget(widgetId)

        assertTrue(WidgetClockStateStore.isBaselineReady(realContext, widgetId))
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `missing clock theme preference falls back to light widget styling`() = runBlocking {
        val prefsWithoutTheme: Preferences = preferencesOf(
            booleanPreferencesKey("use_24h_clock") to true,
            booleanPreferencesKey("show_date_in_widget") to false,
            booleanPreferencesKey("flip_animation_enabled") to true,
            stringPreferencesKey("temperature_unit") to "CELSIUS",
            stringPreferencesKey("clock_tile_size") to "MEDIUM"
        )

        val dataStore: DataStore<Preferences> = mockk()
        every { entryPoint.dataStore() } returns dataStore
        every { dataStore.data } returns flowOf(prefsWithoutTheme)

        val updaterWithoutTheme = TestWidgetUpdater(mockContext, appWidgetManager, entryPoint)

        updaterWithoutTheme.updateWidget(widgetId)

        verify(exactly = 4) {
            anyConstructed<RemoteViews>().setInt(any(), "setBackgroundResource", R.drawable.flip_digit_bg_light)
        }
        verify(atLeast = 6) {
            anyConstructed<RemoteViews>().setTextColor(any(), android.graphics.Color.BLACK)
        }
    }

    // ── clearDigits forces full re-render ──────────────────────

    @Test
    fun `clearDigits causes next updateWidget to use updateAppWidget`() = runBlocking {
        // Establish rendered state
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        WidgetClockStateStore.clearDigits(realContext, widgetId)
        clearMocks(appWidgetManager, answers = false)

        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
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
        confirmVerified(appWidgetManager)
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
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `updateClockOnly with allowAnimation true still suppresses during no-animation window`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute
        )

        // Previous rendered state is exactly one minute behind -> INCREMENTAL mode.
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 25, true))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 1)
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(realContext, widgetId, currentMinute + 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        updater.updateClockOnly(widgetId, allowAnimation = true)

        // Suppression window must override animation path: no setDisplayedChild() calls.
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic updateClockOnly syncs only changed digit fold overlays without animation`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute
        )

        val atomicUpdater = AtomicTestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 25, true))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        atomicUpdater.updateClockOnly(widgetId, allowAnimation = true)

        // Zero animations — no setDisplayedChild calls anywhere
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) }
        // Changed digit m2 (5→6): both fold overlay views updated to "6"
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_from, "6") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_to, "6") }
        // Base digit text should also update
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m2, "6") }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic suppression path avoids fold flipper resets`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute
        )

        val atomicUpdater = AtomicTestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 25, true))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 1)
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(realContext, widgetId, currentMinute + 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        atomicUpdater.updateClockOnly(widgetId, allowAnimation = true)

        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(R.id.fold_h1, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(R.id.fold_h2, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(R.id.fold_m1, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(R.id.fold_m2, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic updateClockOnly syncs ALL changed digit overlays when multiple digits change`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 20),
            epochMinute = currentMinute
        )

        val atomicUpdater = AtomicTestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
        // 10:19 → 10:20: m1 (1→2) and m2 (9→0) both change
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 19, true))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        atomicUpdater.updateClockOnly(widgetId, allowAnimation = true)

        // Zero animations — no setDisplayedChild calls
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) }
        // Changed digits m1 (1→2) and m2 (9→0): both overlay views updated
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m1_from, "2") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m1_to, "2") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_from, "0") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_to, "0") }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic updateClockOnly syncs all four digit overlays at hour boundary`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 0),
            epochMinute = currentMinute
        )

        val atomicUpdater = AtomicTestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
        // 09:59 → 10:00: all four digits change
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(9, 59, true))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        atomicUpdater.updateClockOnly(widgetId, allowAnimation = true)

        // Zero animations — no setDisplayedChild calls
        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) }
        // All 4 digits changed: 09:59 → 10:00. Verify fold overlay text updates.
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_h1_from, "1") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_h1_to, "1") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_h2_from, "0") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_h2_to, "0") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m1_from, "0") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m1_to, "0") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_from, "0") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_to, "0") }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic updateClockOnly avoids fold flipper animation during quiet render`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute
        )

        val atomicUpdater = AtomicTestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
        // 10:25 → 10:26: only m2 changes
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 25, true))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 1)
        // Suppress animation for this test
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(realContext, widgetId, currentMinute + 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        kotlinx.coroutines.delay(100)

        atomicUpdater.updateClockOnly(widgetId, allowAnimation = true)

        verify(exactly = 0) { anyConstructed<RemoteViews>().setDisplayedChild(any(), any()) }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_from, "6") }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.fold_m2_to, "6") }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    // ── Multiple consecutive calls ──────────────────────────────

    @Test
    fun `two consecutive updateWidget calls use full then partial`() = runBlocking {
        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        clearMocks(appWidgetManager, answers = false)

        updater.updateWidget(widgetId)
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `no location first render still uses updateAppWidget`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        assertNotNull(WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId))
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `no location subsequent render uses partiallyUpdateAppWidget`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `same minute non-tick update preserves existing clock digit views`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute
        )

        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 26, true))
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute)

        updater.updateWidget(widgetId, isMinuteTick = false, allowWeatherRefresh = false)

        // In preservation mode, clock digit child visibility should not be rebound.
        verify(exactly = 0) { anyConstructed<RemoteViews>().setViewVisibility(12345, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic hour rollover 959 to 1000 animates all 4 digits`() = runBlocking {
        mockkObject(ClockSnapshot.Companion)
        val currentMinute = System.currentTimeMillis() / 60000L
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 0),
            epochMinute = currentMinute
        )

        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(9, 59, true))
        updater.updateClockOnly(widgetId, allowAnimation = true)

        // All 4 digits changed: 09:59 → 10:00
        // Verify animation path was triggered (setDisplayedChild calls for all 4)
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic midnight rollover 2359 to 0000 animates all 4 digits 24h`() = runBlocking {
        mockkObject(ClockSnapshot.Companion)
        val currentMinute = System.currentTimeMillis() / 60000L
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(0, 0),
            epochMinute = currentMinute
        )

        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(23, 59, true))
        updater.updateClockOnly(widgetId, allowAnimation = true)

        // All 4 digits changed: 23:59 → 00:00
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic noon transition 1159 AM to 1200 PM animates digits 12h mode`() = runBlocking {
        mockkObject(ClockSnapshot.Companion)
        val currentMinute = System.currentTimeMillis() / 60000L
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(12, 0),
            epochMinute = currentMinute
        )

        // 11:59 AM in 12h format is digits 1,1,5,9
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(11, 59, false))
        updater.updateClockOnly(widgetId, allowAnimation = true)

        // 12:00 PM in 12h format is digits 1,2,0,0 (h2 and both minute digits change)
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `atomic 12h mode midnight 1159 PM to 1200 AM animates all 4 digits`() = runBlocking {
        mockkObject(ClockSnapshot.Companion)
        val currentMinute = System.currentTimeMillis() / 60000L
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(0, 0),
            epochMinute = currentMinute
        )

        // 11:59 PM in 12h format is digits 1,1,5,9
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(23, 59, false))
        updater.updateClockOnly(widgetId, allowAnimation = true)

        // 12:00 AM in 12h format is digits 1,2,0,0
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }
}
