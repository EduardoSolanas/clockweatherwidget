package com.clockweather.app.presentation.widget.common

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.repository.LocationRepository
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

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
        stringPreferencesKey("clock_tile_size") to "MEDIUM",
        floatPreferencesKey("date_font_size_sp") to 15f,
    )

    private class TestWidgetUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint,
    ) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {
        override val layoutResId = R.layout.widget_compact
        override val rootViewId = R.id.widget_root
        override val dateViewId = R.id.widget_date

        override fun bindExtra(
            views: RemoteViews,
            weather: WeatherData,
            tempUnit: TemperatureUnit,
            prefs: Preferences,
        ) {
        }
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

        mockResources = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns realContext.packageName
        every { mockContext.applicationContext } returns realContext
        every { mockContext.resources } returns mockResources
        every { mockContext.getColor(R.color.flip_digit_text_light) } returns Color.BLACK
        every { mockContext.getColor(R.color.flip_digit_text_dark) } returns Color.WHITE
        every { mockResources.getDimension(any()) } returns 48f

        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewTextSize(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewLayoutHeight(any(), any(), any()) } just Runs

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()

        WidgetClockStateStore.clearWidget(realContext, widgetId)
        updater = TestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `first render uses updateAppWidget`() = runBlocking {
        assertNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))

        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        assertNotNull(WidgetClockStateStore.getLastDigits(realContext, widgetId))
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `subsequent render uses partiallyUpdateAppWidget`() = runBlocking {
        // Simulate a prior completed render: baseline ready AND digits saved.
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        updater.updateWidget(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `missing clock theme preference falls back to light widget styling`() = runBlocking {
        val prefsWithoutTheme: Preferences = preferencesOf(
            booleanPreferencesKey("use_24h_clock") to true,
            booleanPreferencesKey("show_date_in_widget") to false,
            booleanPreferencesKey("flip_animation_enabled") to true,
            stringPreferencesKey("temperature_unit") to "CELSIUS",
            stringPreferencesKey("clock_tile_size") to "MEDIUM",
        )

        val dataStore: DataStore<Preferences> = mockk()
        every { entryPoint.dataStore() } returns dataStore
        every { dataStore.data } returns flowOf(prefsWithoutTheme)

        TestWidgetUpdater(mockContext, appWidgetManager, entryPoint).updateWidget(widgetId)

        verify(exactly = 4) {
            anyConstructed<RemoteViews>().setInt(any(), "setBackgroundResource", R.drawable.flip_digit_bg_light)
        }
        verify(atLeast = 6) {
            anyConstructed<RemoteViews>().setTextColor(any(), Color.BLACK)
        }
    }

    @Test
    fun `dark clock theme applies dark tile background and text color`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 4) {
            anyConstructed<RemoteViews>().setInt(any(), "setBackgroundResource", R.drawable.flip_digit_bg)
        }
        verify(atLeast = 6) {
            anyConstructed<RemoteViews>().setTextColor(any(), Color.WHITE)
        }
    }

    @Test
    fun `same minute non tick update preserves existing clock bindings`() = runBlocking {
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute,
        )

        // Simulate completed prior render so the same-minute preservation logic can engage.
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState.from(10, 26, true))
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute)

        updater.updateWidget(widgetId, isMinuteTick = false, allowWeatherRefresh = false)

        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_h1, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_h2, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m1, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m2, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `updateClockOnly uses partiallyUpdateAppWidget when minute changes`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, System.currentTimeMillis() / 60000L - 1)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            CoroutineScope(Dispatchers.Unconfined),
        )
        delay(50)

        updater.updateClockOnly(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_h1, any()) }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_h2, any()) }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m1, any()) }
        verify(exactly = 1) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m2, any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `updateClockOnly skips when same minute already rendered`() = runBlocking {
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.markRendered(realContext, widgetId, System.currentTimeMillis() / 60000L)

        com.clockweather.app.util.WidgetPrefsCache.init(
            mockk<DataStore<Preferences>> { every { data } returns flowOf(prefs) },
            CoroutineScope(Dispatchers.Unconfined),
        )
        delay(50)

        updater.updateClockOnly(widgetId)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_h1, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_h2, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m1, any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setTextViewText(R.id.digit_m2, any()) }
        confirmVerified(appWidgetManager)
    }

    /**
     * Regression guard: clearDigits (settings change) must NOT trigger a full
     * [AppWidgetManager.updateAppWidget] when the baseline is already established.
     * A full replace would flash the layout XML defaults ("0000") for one frame.
     */
    @Test
    fun `clearDigits with active baseline keeps next updateWidget as partial - no 0000 flash`() = runBlocking {
        // Step 1: Establish a baseline (first full render has happened).
        WidgetClockStateStore.markBaselineReady(realContext, widgetId)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, DigitState(1, 4, 3, 7))

        updater.updateWidget(widgetId)
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)

        // Step 2: Settings change — clearDigits must NOT reset the baseline.
        WidgetClockStateStore.clearDigits(realContext, widgetId)
        clearMocks(appWidgetManager, answers = false)

        updater.updateWidget(widgetId)

        // Must stay partial — baseline is still set, so no full layout flash.
        verify(exactly = 0) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
        confirmVerified(appWidgetManager)
    }
}
