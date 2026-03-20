package com.clockweather.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.clockweather.app.presentation.widget.common.DigitState
import com.clockweather.app.presentation.widget.common.WidgetClockStateStore
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import io.mockk.*
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
 * Tests for [ClockWeatherApplication.pushClockInstant].
 *
 * Verifies that:
 * - Widgets are pushed with absolute digit visibility (self-heals host drift)
 * - State is persisted after push
 * - 12h/24h mode is respected
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PushClockInstantTest {

    private lateinit var app: ClockWeatherApplication
    private lateinit var appWidgetManager: AppWidgetManager
    private val realContext: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        appWidgetManager = mockk(relaxed = true)
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager

        // By default, return empty widget IDs for all providers
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf()

        // Use spyk on a real Application — stub context methods it needs
        app = spyk(ClockWeatherApplication())
        // Must use the real Robolectric package name so RemoteViews can resolve layouts
        every { app.packageName } returns realContext.packageName
        // Route SharedPreferences calls through the real Robolectric context
        every { app.applicationContext } returns realContext
        // DateFormat needs a real context
        mockkStatic(android.text.format.DateFormat::class)
        every { android.text.format.DateFormat.is24HourFormat(any()) } returns true

        // Freeze time for deterministic tests
        mockkStatic(LocalTime::class)

        // Ensure the prefs cache doesn't leak state from other tests
        // (getCachedSnapshot returning a stale use_24h_clock=true would bypass DateFormat mock)
        mockkObject(com.clockweather.app.util.WidgetPrefsCache)
        every { com.clockweather.app.util.WidgetPrefsCache.getCachedSnapshot() } returns null

        // Clear any leftover state
        WidgetClockStateStore.clearWidget(realContext, 42)
        WidgetClockStateStore.clearWidget(realContext, 43)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun stubWidgetIds(providerClass: Class<*>, vararg ids: Int) {
        every {
            appWidgetManager.getAppWidgetIds(match<ComponentName> { it.className == providerClass.name })
        } returns ids.toList().toIntArray()
    }

    @Test
    fun `pushClockInstant calls partiallyUpdateAppWidget for active widgets`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)

        app.pushClockInstant()

        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(42, any()) }
    }

    @Test
    fun `pushClockInstant skips providers with no active widgets`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        // No widgets active

        app.pushClockInstant()

        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(any<Int>(), any()) }
    }

    @Test
    fun `pushClockInstant skips widget push when stored digits already match current time`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)

        // Pre-store the exact digits that match 14:37
        WidgetClockStateStore.saveLastDigits(realContext, 42, DigitState(1, 4, 3, 7))

        app.pushClockInstant()

        verify(exactly = 0) { appWidgetManager.partiallyUpdateAppWidget(42, any()) }
    }

    @Test
    fun `pushClockInstant force mode still pushes when stored digits already match current time`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)
        WidgetClockStateStore.saveLastDigits(realContext, 42, DigitState(1, 4, 3, 7))

        app.pushClockInstant(forceAllDigits = true)

        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(42, any()) }
    }

    @Test
    fun `pushClockInstant updates widget when stored digits differ from current time`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)

        // Pre-store stale digits (14:36 → 14:37, only m2 changed)
        WidgetClockStateStore.saveLastDigits(realContext, 42, DigitState(1, 4, 3, 6))

        app.pushClockInstant()

        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(42, any()) }
    }

    @Test
    fun `pushClockInstant persists new digits after push`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)

        app.pushClockInstant()

        val stored = WidgetClockStateStore.getLastDigits(realContext, 42)
        assertNotNull(stored)
        assertEquals(DigitState(1, 4, 3, 7), stored)
    }

    @Test
    fun `pushClockInstant marks epoch minute as rendered`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)

        app.pushClockInstant()

        val rendered = WidgetClockStateStore.getLastRenderedEpochMinute(realContext, 42)
        assertNotNull(rendered)
    }

    @Test
    fun `pushClockInstant handles 12h mode correctly`() {
        // 15:00 in 12h mode → display hour = 3 → digits 0, 3
        every { LocalTime.now() } returns LocalTime.of(15, 0)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)
        every { android.text.format.DateFormat.is24HourFormat(any()) } returns false

        app.pushClockInstant()

        val stored = WidgetClockStateStore.getLastDigits(realContext, 42)
        assertNotNull(stored)
        assertEquals(0, stored!!.h1) // tens of 3 = 0
        assertEquals(3, stored.h2)   // ones of 3 = 3
        assertEquals(0, stored.m1)
        assertEquals(0, stored.m2)
    }

    @Test
    fun `pushClockInstant handles midnight in 12h mode`() {
        // hour=0, 12h mode → display hour = 12
        every { LocalTime.now() } returns LocalTime.of(0, 0)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)
        every { android.text.format.DateFormat.is24HourFormat(any()) } returns false

        app.pushClockInstant()

        val stored = WidgetClockStateStore.getLastDigits(realContext, 42)
        assertNotNull(stored)
        assertEquals(1, stored!!.h1) // tens of 12 = 1
        assertEquals(2, stored.h2)   // ones of 12 = 2
    }

    @Test
    fun `pushClockInstant handles multiple providers and widget IDs`() {
        every { LocalTime.now() } returns LocalTime.of(10, 30)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)
        stubWidgetIds(ExtendedWidgetProvider::class.java, 43)

        app.pushClockInstant()

        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(42, any()) }
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(43, any()) }
    }

    @Test
    fun `pushClockInstant with no previous state updates all digits`() {
        every { LocalTime.now() } returns LocalTime.of(14, 37)
        stubWidgetIds(CompactWidgetProvider::class.java, 42)
        // No previous state stored for widget 42

        app.pushClockInstant()

        // Should push since there's no previous state (prev == null)
        verify(exactly = 1) { appWidgetManager.partiallyUpdateAppWidget(42, any()) }

        // And the state should now be saved
        val stored = WidgetClockStateStore.getLastDigits(realContext, 42)
        assertEquals(DigitState(1, 4, 3, 7), stored)
    }
}
