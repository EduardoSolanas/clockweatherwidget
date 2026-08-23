package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetUpdater
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetUpdater
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetUpdater
import io.mockk.mockk
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

    /**
     * Responsive size breakpoints are disabled (emptyList) until size-differentiated layout
     * variants are implemented. Shipping multiple identical layouts in one RemoteViews payload
     * would multiply icon bitmap allocations and exceed the 1MB Binder transaction limit (TransactionTooLargeException).
     */
    @Test
    @Config(sdk = [31])
    fun `Android 12+ updaters keep responsive breakpoints disabled to protect Binder transaction budget`() {
        val compactUpdater = CompactWidgetUpdater(context, appWidgetManager, entryPoint)
        val extendedUpdater = ExtendedWidgetUpdater(context, appWidgetManager, entryPoint)
        val forecastUpdater = ForecastWidgetUpdater(context, appWidgetManager, entryPoint)

        assertTrue(compactUpdater.getResponsiveSizeBreakpoints().isEmpty())
        assertTrue(extendedUpdater.getResponsiveSizeBreakpoints().isEmpty())
        assertTrue(forecastUpdater.getResponsiveSizeBreakpoints().isEmpty())
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
