package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetUpdateSchedulerTest {

    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        appWidgetManager = mockk(relaxed = true)
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(context) } returns appWidgetManager
    }

    @Test
    fun `sendUpdateBroadcast with isClockTick true sends ACTION_CLOCK_TICK to all providers`() {
        // Setup existing widget IDs for all types
        every { appWidgetManager.getAppWidgetIds(any()) } returns intArrayOf(1)

        WidgetUpdateScheduler.sendUpdateBroadcast(context, isClockTick = true)

        // Verify broadcast sent to CompactWidgetProvider
        verify {
            context.sendBroadcast(match { intent ->
                intent.action == WidgetUpdateScheduler.ACTION_CLOCK_TICK &&
                intent.component?.className == CompactWidgetProvider::class.java.name
            })
        }

        // Verify broadcast sent to ExtendedWidgetProvider
        verify {
            context.sendBroadcast(match { intent ->
                intent.action == WidgetUpdateScheduler.ACTION_CLOCK_TICK &&
                intent.component?.className == ExtendedWidgetProvider::class.java.name
            })
        }

        // Verify broadcast sent to ForecastWidgetProvider
        verify {
            context.sendBroadcast(match { intent ->
                intent.action == WidgetUpdateScheduler.ACTION_CLOCK_TICK &&
                intent.component?.className == ForecastWidgetProvider::class.java.name
            })
        }

        // Verify broadcast sent to LargeWidgetProvider
        verify {
            context.sendBroadcast(match { intent ->
                intent.action == WidgetUpdateScheduler.ACTION_CLOCK_TICK &&
                intent.component?.className == LargeWidgetProvider::class.java.name
            })
        }
    }

    @Test
    fun `sendUpdateBroadcast with isClockTick false sends APPWIDGET_UPDATE to all providers`() {
        every { appWidgetManager.getAppWidgetIds(any()) } returns intArrayOf(1)

        WidgetUpdateScheduler.sendUpdateBroadcast(context, isClockTick = false)

        verify {
            context.sendBroadcast(match { intent ->
                intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
            })
        }
    }
}
