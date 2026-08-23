package com.clockweather.app.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActiveWidgetDetectorTest {

    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        appWidgetManager = mockk(relaxed = true)
    }

    @Test
    fun `hasActiveWidgets returns false when all providers have empty widget IDs`() {
        every { appWidgetManager.getAppWidgetIds(any()) } returns intArrayOf()

        assertFalse(ActiveWidgetDetector.hasActiveWidgets(context, appWidgetManager))
        assertEquals(0, ActiveWidgetDetector.getActiveWidgetCount(context, appWidgetManager))
    }

    @Test
    fun `hasActiveWidgets returns true when at least one provider has active widget IDs`() {
        every {
            appWidgetManager.getAppWidgetIds(ComponentName(context, CompactWidgetProvider::class.java))
        } returns intArrayOf(101, 102)
        every {
            appWidgetManager.getAppWidgetIds(ComponentName(context, ExtendedWidgetProvider::class.java))
        } returns intArrayOf()
        every {
            appWidgetManager.getAppWidgetIds(ComponentName(context, ForecastWidgetProvider::class.java))
        } returns intArrayOf(201)

        assertTrue(ActiveWidgetDetector.hasActiveWidgets(context, appWidgetManager))
        assertEquals(3, ActiveWidgetDetector.getActiveWidgetCount(context, appWidgetManager))
    }
}
