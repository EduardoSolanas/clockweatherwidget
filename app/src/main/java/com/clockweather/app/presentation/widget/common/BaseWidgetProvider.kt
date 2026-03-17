package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.receiver.ClockAlarmReceiver
import dagger.hilt.android.EntryPointAccessors

abstract class BaseWidgetProvider : AppWidgetProvider() {
    
    abstract fun getUpdater(context: Context, appWidgetManager: AppWidgetManager, entryPoint: WidgetEntryPoint): BaseWidgetUpdater

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        android.util.Log.d("ClockWeatherApp", "onUpdate called for ${this::class.simpleName}. IDs count: ${appWidgetIds.size}")
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val updater = getUpdater(context, appWidgetManager, entryPoint)
        appWidgetIds.forEach { updater.updateWidget(it) }
    }



    override fun onEnabled(context: Context) {
        ClockAlarmReceiver.scheduleNextTick(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetClockStateStore.clearWidget(context, it) }
    }

    override fun onDisabled(context: Context) {
        if (ClockAlarmReceiver.hasAnyActiveWidgets(context)) {
            ClockAlarmReceiver.scheduleNextTick(context)
        } else {
            ClockAlarmReceiver.cancelNextTick(context)
        }
    }
}
