package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BaseWidgetProviderTest {

    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        appWidgetManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * When getUpdater throws (e.g. Hilt not initialised, DI failure),
     * the provider must still push a fallback RemoteViews so the widget
     * shows the initial layout instead of "Can't load widget".
     */
    @Test
    fun `onUpdate pushes fallback views when updater throws`() {
        val provider = object : BaseWidgetProvider() {
            override val fallbackLayoutResId: Int get() = R.layout.widget_compact

            override fun getUpdater(
                context: Context,
                appWidgetManager: AppWidgetManager,
                entryPoint: WidgetEntryPoint
            ): BaseWidgetUpdater {
                throw RuntimeException("Hilt not ready")
            }
        }

        val widgetIds = intArrayOf(42)

        // Act — onUpdate should NOT crash
        provider.onUpdate(context, appWidgetManager, widgetIds)

        // Allow the coroutine to complete
        Thread.sleep(500)

        // Verify a fallback RemoteViews was pushed
        verify { appWidgetManager.updateAppWidget(42, any<RemoteViews>()) }
    }

    @Test
    fun `onAppWidgetOptionsChanged pushes fallback views when updater throws`() {
        val provider = object : BaseWidgetProvider() {
            override val fallbackLayoutResId: Int get() = R.layout.widget_compact

            override fun getUpdater(
                context: Context,
                appWidgetManager: AppWidgetManager,
                entryPoint: WidgetEntryPoint
            ): BaseWidgetUpdater {
                throw RuntimeException("Hilt not ready")
            }
        }

        provider.onAppWidgetOptionsChanged(
            context, appWidgetManager, 42, android.os.Bundle()
        )

        Thread.sleep(500)

        verify { appWidgetManager.updateAppWidget(42, any<RemoteViews>()) }
    }
}
