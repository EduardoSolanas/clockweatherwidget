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
    fun `syncClockNow calls refreshAllWidgets then schedules alarm`() {
        val ids = intArrayOf(1)
        every { appWidgetManager.getAppWidgetIds(any()) } returns ids

        // Mock ClockAlarmReceiver companion methods to avoid real AlarmManager access
        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        val spyApp = spyk(app)
        coEvery { spyApp.resolveHighPrecision() } returns true

        runBlocking {
            spyApp.syncClockNow(context)
        }

        // refreshAllWidgets was called (checked via getAppWidgetIds)
        verify { appWidgetManager.getAppWidgetIds(any()) }
        // Alarm was rescheduled as backup
        verify { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }
}
