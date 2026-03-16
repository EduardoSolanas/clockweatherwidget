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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdateScheduler.ACTION_CLOCK_TICK) {
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            val mgr = AppWidgetManager.getInstance(context)
            val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val updater = getUpdater(context, mgr, entryPoint)
            appWidgetIds.forEach { updater.updateClockOnly(it) }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleClockAlarm(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.cancelClockAlarmIfNoWidgets(context)
    }
}
