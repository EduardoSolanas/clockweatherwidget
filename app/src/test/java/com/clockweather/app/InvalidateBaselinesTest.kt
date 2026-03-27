package com.clockweather.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.clockweather.app.presentation.widget.common.DigitState
import com.clockweather.app.presentation.widget.common.WidgetClockStateStore
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
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
 * Tests for [ClockWeatherApplication.invalidateAllWidgetBaselines].
 *
 * Verifies that clearing digit state forces the next [updateWidget] call to
 * use [updateAppWidget] (full replacement) — needed when settings change
 * (theme, tile size) so the full layout/styling is pushed fresh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InvalidateBaselinesTest {

    private lateinit var app: ClockWeatherApplication
    private lateinit var appWidgetManager: AppWidgetManager
    private val realContext: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        appWidgetManager = mockk()
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf()

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager

        app = spyk(ClockWeatherApplication())
        every { app.packageName } returns realContext.packageName
        every { app.applicationContext } returns realContext

        // Clean state
        for (id in listOf(10, 11, 20)) {
            WidgetClockStateStore.clearWidget(realContext, id)
        }
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
    fun `invalidateAllWidgetBaselines clears digits for all providers`() {
        stubWidgetIds(CompactWidgetProvider::class.java, 10)
        stubWidgetIds(ExtendedWidgetProvider::class.java, 11)

        // Store digits for both
        WidgetClockStateStore.saveLastDigits(realContext, 10, DigitState(1, 0, 3, 0))
        WidgetClockStateStore.saveLastDigits(realContext, 11, DigitState(2, 3, 5, 9))

        app.invalidateAllWidgetBaselines()

        // Both should be null now — forcing full updateAppWidget on next render
        assertNull(WidgetClockStateStore.getLastDigits(realContext, 10))
        assertNull(WidgetClockStateStore.getLastDigits(realContext, 11))
        verify(atLeast = 1) { appWidgetManager.getAppWidgetIds(any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `invalidateAllWidgetBaselines handles multiple IDs per provider`() {
        stubWidgetIds(CompactWidgetProvider::class.java, 10, 20)

        WidgetClockStateStore.saveLastDigits(realContext, 10, DigitState(1, 0, 3, 0))
        WidgetClockStateStore.saveLastDigits(realContext, 20, DigitState(2, 3, 5, 9))

        app.invalidateAllWidgetBaselines()

        assertNull(WidgetClockStateStore.getLastDigits(realContext, 10))
        assertNull(WidgetClockStateStore.getLastDigits(realContext, 20))
        verify(atLeast = 1) { appWidgetManager.getAppWidgetIds(any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `invalidateAllWidgetBaselines is safe with no active widgets`() {
        // All providers return empty IDs (default mock)
        // Should not throw
        app.invalidateAllWidgetBaselines()

        verify(atLeast = 1) { appWidgetManager.getAppWidgetIds(any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `invalidateAllWidgetBaselines preserves epoch minute`() {
        stubWidgetIds(CompactWidgetProvider::class.java, 10)
        WidgetClockStateStore.saveLastDigits(realContext, 10, DigitState(1, 0, 3, 0))
        WidgetClockStateStore.markRendered(realContext, 10, 5555L)

        app.invalidateAllWidgetBaselines()

        // Digits cleared, but epoch minute is preserved
        assertNull(WidgetClockStateStore.getLastDigits(realContext, 10))
        assertEquals(5555L, WidgetClockStateStore.getLastRenderedEpochMinute(realContext, 10))
        verify(atLeast = 1) { appWidgetManager.getAppWidgetIds(any()) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `invalidateAllWidgetBaselines queries all four providers`() {
        app.invalidateAllWidgetBaselines()

        verify { appWidgetManager.getAppWidgetIds(match<ComponentName> { it.className == CompactWidgetProvider::class.java.name }) }
        verify { appWidgetManager.getAppWidgetIds(match<ComponentName> { it.className == ExtendedWidgetProvider::class.java.name }) }
        verify { appWidgetManager.getAppWidgetIds(match<ComponentName> { it.className == ForecastWidgetProvider::class.java.name }) }
        verify { appWidgetManager.getAppWidgetIds(match<ComponentName> { it.className == LargeWidgetProvider::class.java.name }) }
        confirmVerified(appWidgetManager)
    }
}
