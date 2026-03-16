package com.clockweather.app.presentation.widget.extended

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors

class ExtendedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val updater = ExtendedWidgetUpdater(context, appWidgetManager, entryPoint)
        appWidgetIds.forEach { widgetId ->
            updater.updateWidget(widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler.scheduleClockAlarm(context)
    }

    override fun onDisabled(context: Context) {
        com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler.cancelClockAlarmIfNoWidgets(context)
    }
}

