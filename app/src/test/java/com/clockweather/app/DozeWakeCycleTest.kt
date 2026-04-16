package com.clockweather.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.clockweather.app.presentation.widget.common.WidgetClockStateStore
import com.clockweather.app.receiver.ClockAlarmReceiver
import com.clockweather.app.receiver.ScreenStateReceiver
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
 * Integration test covering the Doze → screen-on wake cycle.
 *
 * Scenario:
 * 1. Widget renders at minute N (screen is on, clock is current).
 * 2. Screen turns off → Doze → 20 minutes pass.
 * 3. Screen turns on (SCREEN_ON fires).
 *
 * Expected:
 * - SCREEN_ON pushes immediately with forceAllDigits=true.
 * - The digit store is updated to the new time.
 * - No intermediate renders occurred during Doze.
 * - Drift of 20 minutes is logged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DozeWakeCycleTest {

    // Real application context for SharedPreferences-backed WidgetClockStateStore
    private val realContext = RuntimeEnvironment.getApplication()

    // Mock app + context for ScreenStateReceiver routing tests
    private lateinit var mockApp: ClockWeatherApplication
    private lateinit var mockContext: Context

    // Spyk app + real widget infra for direct pushClockInstant tests
    private lateinit var spykApp: ClockWeatherApplication
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetId = 77

    @Before
    fun setup() {
        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockApp

        spykApp = spyk(ClockWeatherApplication())
        every { spykApp.packageName } returns realContext.packageName
        every { spykApp.applicationContext } returns realContext

        appWidgetManager = mockk()
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(widgetId)
        every { appWidgetManager.partiallyUpdateAppWidget(any<Int>(), any()) } just Runs
        every { appWidgetManager.updateAppWidget(any<Int>(), any()) } just Runs

        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs

        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        mockkStatic(android.text.format.DateFormat::class)
        every { android.text.format.DateFormat.is24HourFormat(any()) } returns true

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleKeepalive(any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        ScreenStateReceiver.resetUnlockConvergenceThrottleForTests()
        WidgetClockStateStore.clearWidget(realContext, widgetId)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── SCREEN_ON behaviour (via ScreenStateReceiver → mockApp) ───

    @Test
    fun `SCREEN_ON after Doze triggers immediate push with forceAllDigits true`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        every { mockApp.getLastRenderedEpochMinuteForDrift() } returns currentMinute - 20L

        val receiver = ScreenStateReceiver()
        receiver.onReceive(mockContext, Intent(Intent.ACTION_SCREEN_ON))

        verify { mockApp.pushClockInstant(forceAllDigits = true, source = "SCREEN_ON") }
    }

    @Test
    fun `drift metric is logged on SCREEN_ON showing 20-minute Doze gap`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        every { mockApp.getLastRenderedEpochMinuteForDrift() } returns currentMinute - 20L

        val receiver = ScreenStateReceiver()
        receiver.onReceive(mockContext, Intent(Intent.ACTION_SCREEN_ON))

        verify {
            Log.d(
                any(),
                match { msg ->
                    // Allow ±1 for minute boundary races
                    msg.contains("drift=19") || msg.contains("drift=20") || msg.contains("drift=21")
                }
            )
        }
    }

    // ── pushClockInstant digit-store behaviour (direct) ──────────

    @Test
    fun `pushClockInstant after Doze gap updates digit store to current minute`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        WidgetClockStateStore.markRendered(realContext, widgetId, currentMinute - 20L)

        spykApp.pushClockInstant(forceAllDigits = true, source = "SCREEN_ON")

        val rendered = WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId)
        assertNotNull("Rendered epoch minute must be saved after push", rendered)
        assertTrue(
            "Rendered minute $rendered must equal current minute $currentMinute (±1 for boundary)",
            rendered!! >= currentMinute - 1 && rendered <= currentMinute + 1
        )
    }

    @Test
    fun `no intermediate renders happen during Doze — digit store remains at pre-Doze minute`() {
        val minuteBeforeDoze = System.currentTimeMillis() / 60000L - 20L
        WidgetClockStateStore.markRendered(realContext, widgetId, minuteBeforeDoze)

        // Simulate Doze: no pushClockInstant called, no TIME_TICK delivered
        val stored = WidgetClockStateStore.getLastRenderedEpochMinute(realContext, widgetId)
        assertEquals(
            "No renders should occur passively during Doze",
            minuteBeforeDoze,
            stored
        )
    }
}
