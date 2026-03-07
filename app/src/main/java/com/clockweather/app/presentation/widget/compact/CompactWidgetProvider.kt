package com.clockweather.app.presentation.widget.compact

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors

class CompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val updater = CompactWidgetUpdater(context, appWidgetManager, entryPoint)
        appWidgetIds.forEach { widgetId ->
            updater.updateWidget(widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler.scheduleClockAlarm(context)
    }

    override fun onDisabled(context: Context) {
        // Cancel clock alarm only if no extended widgets remain either
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val extendedIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider::class.java)
        )
        if (extendedIds.isEmpty()) {
            com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler.cancelClockAlarm(context)
        }
    }
}

