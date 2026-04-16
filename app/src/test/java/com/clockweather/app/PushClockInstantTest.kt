package com.clockweather.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import android.widget.RemoteViews
import com.clockweather.app.presentation.widget.common.ClockSnapshot
import com.clockweather.app.presentation.widget.common.DigitState
import com.clockweather.app.presentation.widget.common.WidgetClockStateStore
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [ClockWeatherApplication.pushClockInstant].
 *
 * Critical invariants:
 * 1. ALWAYS uses [AppWidgetManager.partiallyUpdateAppWidget] — never [updateAppWidget].
 *    A full replace would flash the XML-default "0000" for one frame.
 * 2. Saves the pushed digits to [WidgetClockStateStore] so the next delta push is accurate.
 * 3. Marks the rendered epoch minute so duplicate-minute guards work correctly.
 * 4. With no prior digits in store (first push), all four digits are always written.
 * 5. [forceAllDigits]=true always writes all four digits regardless of previous state.
 * 6. When current digits match stored digits and forceAllDigits=false, no push is made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PushClockInstantTest {

    /** Robolectric's real application context — used for SharedPreferences backing [WidgetClockStateStore]. */
    private val realContext = RuntimeEnvironment.getApplication()

    private lateinit var app: ClockWeatherApplication
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetId = 99

    @Before
    fun setup() {
        // Use a spy so we can override packageName while still delegating state-store
        // calls (getSharedPreferences) to the real Robolectric application context.
        app = spyk(ClockWeatherApplication())
        every { app.packageName } returns realContext.packageName
        every { app.applicationContext } returns realContext

        // DateFormat.is24HourFormat(this) fails when the Application has no base context;
        // mock it to always return true so the test is stable regardless of locale.
        mockkStatic(android.text.format.DateFormat::class)
        every { android.text.format.DateFormat.is24HourFormat(any()) } returns true

        appWidgetManager = mockk()
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(widgetId)
        every { appWidgetManager.partiallyUpdateAppWidget(any<Int>(), any()) } just Runs
        every { appWidgetManager.updateAppWidget(any<Int>(), any()) } just Runs

        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewTextSize(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs

        // Avoid noisy log output in test reports.
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0

        // Clean state so each test starts fresh.
        WidgetClockStateStore.clearWidget(realContext, widgetId)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── Core invariant: only partial updates ─────────────────────

    @Test
    fun `pushClockInstant NEVER calls updateAppWidget`() {
        app.pushClockInstant(forceAllDigits = true)

        verify(exactly = 0) { appWidgetManager.updateAppWidget(any<Int>(), any()) }
    }

    @Test
    fun `pushClockInstant always calls partiallyUpdateAppWidget when there are changes`() {
        app.pushClockInstant(forceAllDigits = true)

        verify(atLeast = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    // ── State persistence ────────────────────────────────────────

    @Test
    fun `pushClockInstant saves digits to WidgetClockStateStore`() {
        app.pushClockInstant(forceAllDigits = true)

        assertNotNull(
            "Digits must be persisted after a push so the next delta is accurate",
            WidgetClockStateStore.getLastDigits(realContext, widgetId)
        )
    }

    @Test
    fun `pushClockInstant marks the rendered epoch minute`() {
        val before = System.currentTimeMillis() / 60000L

        app.pushClockInstant(forceAllDigits = true)

        val after = System.currentTimeMillis() / 60000L
        val rendered = WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId)

        assertNotNull("Epoch minute must be saved after push", rendered)
        assertTrue(
            "Rendered minute $rendered must be within [$before, $after]",
            rendered!! in before..after
        )
    }

    // ── No previous digits → always push ────────────────────────

    @Test
    fun `pushClockInstant with no prior digits always pushes all views`() {
        // Store is empty — first push must always write digits.
        WidgetClockStateStore.clearWidget(realContext, widgetId)

        app.pushClockInstant(forceAllDigits = false)

        verify(atLeast = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    // ── forceAllDigits flag ──────────────────────────────────────

    @Test
    fun `pushClockInstant with forceAllDigits true always pushes even with matching prior digits`() {
        // Pre-load digits that match current time so a delta push would be a no-op.
        val snapshot = ClockSnapshot.now()
        val now = snapshot.localTime
        // Use 24h=true to avoid ambiguity — we'll force a push regardless.
        val digits = DigitState.from(now.hour, now.minute, is24h = true)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, digits)

        app.pushClockInstant(forceAllDigits = true)

        // forceAllDigits overrides the "no change" delta logic.
        verify(atLeast = 1) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    // ── Delta no-op ───────────────────────────────────────────────

    @Test
    fun `pushClockInstant with unchanged digits and forceAllDigits false is a no-op`() {
        // Compute the exact digits pushClockInstant will compute for the current minute.
        // DateFormat.is24HourFormat is already mocked to return true in @Before.
        val snapshot = ClockSnapshot.now()
        val now = snapshot.localTime

        val digits = DigitState.from(now.hour, now.minute, is24h = true)
        WidgetClockStateStore.saveLastDigits(realContext, widgetId, digits)

        app.pushClockInstant(forceAllDigits = false)

        // No digit changed → partiallyUpdateAppWidget must not be called.
        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(widgetId, any()) }
    }

    // ── Structured source log ────────────────────────────────────

    @Test
    fun `pushClockInstant log includes source field when source is provided`() {
        val logMessages = mutableListOf<String>()
        every { Log.d("ClockWeatherApp", capture(logMessages)) } returns 0

        app.pushClockInstant(forceAllDigits = true, source = "TIME_TICK")

        assertTrue(
            "CLOCK_TRACE log must contain source=TIME_TICK",
            logMessages.any { it.contains("source=TIME_TICK") }
        )
    }

    @Test
    fun `pushClockInstant log includes source=unknown when source not provided`() {
        val logMessages = mutableListOf<String>()
        every { Log.d("ClockWeatherApp", capture(logMessages)) } returns 0

        app.pushClockInstant(forceAllDigits = true)

        assertTrue(
            "CLOCK_TRACE log must contain source=unknown by default",
            logMessages.any { it.contains("source=unknown") }
        )
    }

    // ── suppressAnimationWindow ──────────────────────────────────

    @Test
    fun `pushClockInstant with suppressAnimationWindow arms the no-anim store flag`() {
        val before = System.currentTimeMillis() / 60000L

        app.pushClockInstant(forceAllDigits = true, suppressAnimationWindow = true)

        // The no-anim flag should be set for the current minute+1 so the next tick
        // renders quietly without animation.
        assertTrue(
            "Animation suppression flag must be set after suppress push",
            WidgetClockStateStore.shouldSuppressAnimation(realContext, widgetId, before)
        )
    }
}

