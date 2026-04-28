package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import com.clockweather.app.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    abstract fun getUpdater(context: Context, appWidgetManager: AppWidgetManager, entryPoint: WidgetEntryPoint): BaseWidgetUpdater

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
}
