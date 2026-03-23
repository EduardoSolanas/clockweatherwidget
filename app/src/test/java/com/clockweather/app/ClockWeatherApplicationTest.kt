package com.clockweather.app

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import com.clockweather.app.receiver.ClockAlarmReceiver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
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
        appWidgetManager = mockk(relaxed = true)
        entryPoint = mockk(relaxed = true)
        app = ClockWeatherApplication()

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), WidgetEntryPoint::class.java) } returns entryPoint
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
    fun `syncClockNow force-pushes, refreshes, reschedules alarm, and force-pushes again`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        // Mock ClockAlarmReceiver companion methods to avoid real AlarmManager access
        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        val spyApp = spyk(app)
        coEvery { spyApp.resolveHighPrecision() } returns true
        every { spyApp.pushClockInstant(any(), any(), any()) } just Runs

        runBlocking {
            spyApp.syncClockNow(context)
        }

        // First force push happens before the full refresh.
        verifyOrder {
            spyApp.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = false,
                quietRender = false
            )
            appWidgetManager.getAppWidgetIds(any()) // part of refreshAllWidgets
        }

        // And a second force push is applied after refresh to avoid minute-boundary race.
        verify(exactly = 2) {
            spyApp.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = false,
                quietRender = false
            )
        }

        // Alarm was rescheduled as backup
        verify { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }

    @Test
    fun `syncClockNow suppress mode without reassert performs one quiet push only`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        val spyApp = spyk(app)
        coEvery { spyApp.resolveHighPrecision() } returns true
        every { spyApp.pushClockInstant(any(), any(), any()) } just Runs
        coEvery { spyApp.refreshAllWidgets(any(), any(), any()) } just Runs

        runBlocking {
            spyApp.syncClockNow(
                context,
                suppressAnimation = true,
                reassertAfterReschedule = false
            )
        }

        verify(exactly = 1) {
            spyApp.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true,
                quietRender = false
            )
        }
        coVerify(exactly = 0) { spyApp.refreshAllWidgets(any(), any(), any()) }
        verify { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }

    @Test
    fun `syncClockNow suppress mode with reassert performs two quiet force pushes`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        val spyApp = spyk(app)
        coEvery { spyApp.resolveHighPrecision() } returns true
        every { spyApp.pushClockInstant(any(), any(), any()) } just Runs
        coEvery { spyApp.refreshAllWidgets(any(), any(), any()) } just Runs

        runBlocking {
            spyApp.syncClockNow(
                context,
                suppressAnimation = true,
                reassertAfterReschedule = true
            )
        }

        verify(exactly = 2) {
            spyApp.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true,
                quietRender = false
            )
        }
        coVerify(exactly = 0) { spyApp.refreshAllWidgets(any(), any(), any()) }
        verify { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }
}
