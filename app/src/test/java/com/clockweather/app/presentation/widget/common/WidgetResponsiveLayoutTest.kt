package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetUpdater
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetUpdater
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetUpdater
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WidgetResponsiveLayoutTest {

    private lateinit var context: Context
    private val appWidgetManager: AppWidgetManager = mockk(relaxed = true)
    private val entryPoint: WidgetEntryPoint = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    @Config(sdk = [31])
    fun `Android 12+ compact updater returns responsive dp breakpoints`() {
        val updater = CompactWidgetUpdater(context, appWidgetManager, entryPoint)
        val breakpoints = updater.getResponsiveSizeBreakpoints()

        assertEquals(2, breakpoints.size)
        assertTrue(breakpoints.contains(SizeF(180f, 110f)))
        assertTrue(breakpoints.contains(SizeF(250f, 180f)))
    }

    @Test
    @Config(sdk = [31])
    fun `Android 12+ extended updater returns responsive dp breakpoints`() {
        val updater = ExtendedWidgetUpdater(context, appWidgetManager, entryPoint)
        val breakpoints = updater.getResponsiveSizeBreakpoints()

        assertEquals(2, breakpoints.size)
        assertTrue(breakpoints.contains(SizeF(400f, 140f)))
        assertTrue(breakpoints.contains(SizeF(520f, 180f)))
    }

    @Test
    @Config(sdk = [31])
    fun `Android 12+ forecast updater returns responsive dp breakpoints`() {
        val updater = ForecastWidgetUpdater(context, appWidgetManager, entryPoint)
        val breakpoints = updater.getResponsiveSizeBreakpoints()

        assertEquals(3, breakpoints.size)
        assertTrue(breakpoints.contains(SizeF(180f, 120f)))
        assertTrue(breakpoints.contains(SizeF(250f, 160f)))
        assertTrue(breakpoints.contains(SizeF(350f, 220f)))
    }

    @Test
    @Config(sdk = [29])
    fun `Pre-Android 12 updaters return empty responsive breakpoints for single layout fallback`() {
        val compactUpdater = CompactWidgetUpdater(context, appWidgetManager, entryPoint)
        val extendedUpdater = ExtendedWidgetUpdater(context, appWidgetManager, entryPoint)
        val forecastUpdater = ForecastWidgetUpdater(context, appWidgetManager, entryPoint)

        assertTrue(compactUpdater.getResponsiveSizeBreakpoints().isEmpty())
        assertTrue(extendedUpdater.getResponsiveSizeBreakpoints().isEmpty())
        assertTrue(forecastUpdater.getResponsiveSizeBreakpoints().isEmpty())
    }
}
