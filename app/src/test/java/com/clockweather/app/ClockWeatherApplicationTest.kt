package com.clockweather.app

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.common.WidgetClockStateStore
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import com.clockweather.app.receiver.ClockAlarmReceiver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.EntryPointAccessors

@RunWith(RobolectricTestRunner::class)
class ClockWeatherApplicationTest {

    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var entryPoint: WidgetEntryPoint
    private lateinit var app: ClockWeatherApplication

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        appWidgetManager = mockk()
        entryPoint = mockk(relaxed = true)
        app = ClockWeatherApplication()

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        every { appWidgetManager.getAppWidgetIds(any()) } returns intArrayOf()
        every { appWidgetManager.updateAppWidget(any<Int>(), any()) } just Runs
        every { appWidgetManager.partiallyUpdateAppWidget(any<Int>(), any()) } just Runs

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), WidgetEntryPoint::class.java) } returns entryPoint
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `refreshAllWidgets calls updateClockOnly on all active widgets when isClockTick is true`() {
        val ids = intArrayOf(1, 2)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        runBlocking {
            app.refreshAllWidgets(context, isClockTick = true)
        }

        verify { appWidgetManager.getAppWidgetIds(match { it.className == CompactWidgetProvider::class.java.name }) }
        verify { appWidgetManager.getAppWidgetIds(match { it.className == ExtendedWidgetProvider::class.java.name }) }
        verify { appWidgetManager.getAppWidgetIds(match { it.className == ForecastWidgetProvider::class.java.name }) }
        verify { appWidgetManager.getAppWidgetIds(match { it.className == LargeWidgetProvider::class.java.name }) }
    }

    @Test
    fun `areAllActiveWidgetBaselinesReady returns true when every active widget is ready`() {
        val baselineApp = spyk(app)
        every { baselineApp.packageName } returns "com.clockweather.app"
        every { appWidgetManager.getAppWidgetIds(any()) } returnsMany listOf(
            intArrayOf(1),
            intArrayOf(),
            intArrayOf(2),
            intArrayOf()
        )
        mockkObject(WidgetClockStateStore)
        every { WidgetClockStateStore.isBaselineReady(baselineApp, any()) } returns true

        val result = baselineApp.areAllActiveWidgetBaselinesReady()

        assertTrue(result)
    }

    @Test
    fun `areAllActiveWidgetBaselinesReady returns false when any active widget lacks a baseline`() {
        val baselineApp = spyk(app)
        every { baselineApp.packageName } returns "com.clockweather.app"
        every { appWidgetManager.getAppWidgetIds(any()) } returnsMany listOf(
            intArrayOf(1),
            intArrayOf(),
            intArrayOf(2),
            intArrayOf()
        )
        mockkObject(WidgetClockStateStore)
        every { WidgetClockStateStore.isBaselineReady(baselineApp, 1) } returns true
        every { WidgetClockStateStore.isBaselineReady(baselineApp, 2) } returns false

        val result = baselineApp.areAllActiveWidgetBaselinesReady()

        assertFalse(result)
    }

    @Test
    fun `syncClockOnActivityStop uses quiet delta push when widgets are active`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.hasAnyActiveWidgets(context) } returns true

        val spyApp = spyk(app)
        every { spyApp.pushClockInstant(any(), any(), any(), any()) } just Runs

        spyApp.syncClockOnActivityStop(context)

        verify(exactly = 1) {
            spyApp.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true,
                alignDisplayedChildrenOnly = false
            )
        }
    }

    @Test
    fun `syncClockOnActivityStop skips push when no widgets are active`() {
        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.hasAnyActiveWidgets(context) } returns false

        val spyApp = spyk(app)
        every { spyApp.pushClockInstant(any(), any(), any(), any()) } just Runs

        spyApp.syncClockOnActivityStop(context)

        verify(exactly = 0) { spyApp.pushClockInstant(any(), any(), any(), any()) }
    }

    @Test
    fun `syncClockNow performs single quiet delta push then refresh and reschedules alarm`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        // Mock ClockAlarmReceiver companion methods to avoid real AlarmManager access
        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        val spyApp = spyk(app)
        coEvery { spyApp.resolveHighPrecision() } returns true
        every { spyApp.pushClockInstant(any(), any(), any(), any()) } just Runs

        runBlocking {
            spyApp.syncClockNow(context)
        }

        // Initial quiet delta push happens before the full refresh.
        verifyOrder {
            spyApp.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = false,
                quietRender = true,
                alignDisplayedChildrenOnly = false
            )
            appWidgetManager.getAppWidgetIds(any()) // part of refreshAllWidgets
        }

        // A second quiet push reasserts the correct minute after the full refresh.
        verify(exactly = 2) {
            spyApp.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = false,
                quietRender = true,
                alignDisplayedChildrenOnly = false
            )
        }

        // Alarm was rescheduled as backup
        verify { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }

    @Test
    fun `syncClockNow suppress mode performs single quiet delta push only`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        val spyApp = spyk(app)
        coEvery { spyApp.resolveHighPrecision() } returns true
        every { spyApp.pushClockInstant(any(), any(), any(), any()) } just Runs
        coEvery { spyApp.refreshAllWidgets(any(), any(), any()) } just Runs

        runBlocking {
            spyApp.syncClockNow(
                context,
                suppressAnimation = true
            )
        }

        verify(exactly = 2) {
            spyApp.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true,
                alignDisplayedChildrenOnly = false
            )
        }
        coVerify(exactly = 0) { spyApp.refreshAllWidgets(any(), any(), any()) }
        verify { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }
}
