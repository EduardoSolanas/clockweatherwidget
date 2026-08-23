package com.clockweather.app.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider

/**
 * Utility to inspect placed widget instances across all widget providers.
 *
 * Used to gate background periodic updates and passive location harvesting so
 * the app consumes zero background resources when no widgets exist on the home screen.
 */
object ActiveWidgetDetector {

    val widgetProviderClasses = listOf(
        CompactWidgetProvider::class.java,
        ExtendedWidgetProvider::class.java,
        ForecastWidgetProvider::class.java,
    )

    fun hasActiveWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ): Boolean {
        return widgetProviderClasses.any { cls ->
            appWidgetManager.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()
        }
    }

    fun getActiveWidgetCount(
        context: Context,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ): Int {
        return widgetProviderClasses.sumOf { cls ->
            appWidgetManager.getAppWidgetIds(ComponentName(context, cls)).size
        }
    }
}
