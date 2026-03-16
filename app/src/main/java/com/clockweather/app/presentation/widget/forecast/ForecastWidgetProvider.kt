package com.clockweather.app.presentation.widget.forecast

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler
import dagger.hilt.android.EntryPointAccessors

class ForecastWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val updater = ForecastWidgetUpdater(context, appWidgetManager, entryPoint)
        appWidgetIds.forEach { updater.updateWidget(it) }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleClockAlarm(context)
    }

    override fun onDisabled(context: Context) {
        com.clockweather.app.presentation.widget.common.WidgetUpdateScheduler.cancelClockAlarmIfNoWidgets(context)
    }
}

