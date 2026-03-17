package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.clockweather.app.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors

abstract class BaseWidgetProvider : AppWidgetProvider() {
    
    abstract fun getUpdater(context: Context, appWidgetManager: AppWidgetManager, entryPoint: WidgetEntryPoint): BaseWidgetUpdater

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val updater = getUpdater(context, appWidgetManager, entryPoint)
        appWidgetIds.forEach { updater.updateWidget(it) }
    }



    override fun onEnabled(context: Context) {
        // No longer need manual alarm scheduling; 
        // ACTION_TIME_TICK is handled by ClockWeatherApplication
    }

    override fun onDisabled(context: Context) {
        // No longer need manual alarm cancellation
    }
}
