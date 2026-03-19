package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.receiver.ClockAlarmReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseWidgetProvider : AppWidgetProvider() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    abstract fun getUpdater(context: Context, appWidgetManager: AppWidgetManager, entryPoint: WidgetEntryPoint): BaseWidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Widget providers only receive widget-related intents (ACTION_APPWIDGET_UPDATE, etc.).
        // ACTION_USER_PRESENT is handled by the dynamically-registered ScreenStateReceiver.
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("ClockWeatherApp", "onUpdate called for ${this::class.simpleName}. IDs count: ${appWidgetIds.size}")
        val pendingResult = goAsync()
        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                val updater = getUpdater(context, appWidgetManager, entryPoint)
                appWidgetIds.forEach { updater.updateWidget(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        // First widget of this type placed — make sure alarm + screen receiver are active
        val app = context.applicationContext as? ClockWeatherApplication
        app?.registerScreenStateReceiver()

        scope.launch {
            val isHighPrecision = app?.resolveHighPrecision() ?: true
            ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetClockStateStore.clearWidget(context, it) }
    }

    override fun onDisabled(context: Context) {
        if (ClockAlarmReceiver.hasAnyActiveWidgets(context)) {
            scope.launch {
                val app = context.applicationContext as? ClockWeatherApplication
                val isHighPrecision = app?.resolveHighPrecision() ?: true
                ClockAlarmReceiver.scheduleNextTick(context, isHighPrecision)
            }
        } else {
            // Last widget removed — cancel alarm and unregister screen receiver
            ClockAlarmReceiver.cancelNextTick(context)
            val app = context.applicationContext as? ClockWeatherApplication
            app?.unregisterScreenStateReceiver()
        }
    }
}
