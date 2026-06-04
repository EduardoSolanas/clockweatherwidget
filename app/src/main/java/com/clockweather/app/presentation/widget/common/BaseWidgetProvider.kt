package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.clockweather.app.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    abstract fun getUpdater(context: Context, appWidgetManager: AppWidgetManager, entryPoint: WidgetEntryPoint): BaseWidgetUpdater

    /** Layout resource to push as a fallback when the full update fails. */
    abstract val fallbackLayoutResId: Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("ClockWeatherApp", "onUpdate called for ${this::class.simpleName}. IDs count: ${appWidgetIds.size}")

        // Push fallback layout immediately so the widget never shows "Can't load widget"
        // (critical on Xiaomi/MIUI where the app process may not be alive yet).
        appWidgetIds.forEach { id ->
            try {
                val fallback = RemoteViews(context.packageName, fallbackLayoutResId)
                appWidgetManager.updateAppWidget(id, fallback)
            } catch (_: Throwable) { }
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                val updater = getUpdater(context, appWidgetManager, entryPoint)
                appWidgetIds.forEach { updater.updateWidget(it) }
            } catch (e: Throwable) {
                Log.e("ClockWeatherApp", "onUpdate failed for ${this@BaseWidgetProvider::class.simpleName}", e)
                appWidgetIds.forEach { id ->
                    try {
                        val fallback = RemoteViews(context.packageName, fallbackLayoutResId)
                        appWidgetManager.updateAppWidget(id, fallback)
                    } catch (inner: Throwable) {
                        Log.e("ClockWeatherApp", "Fallback also failed for widget $id", inner)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        Log.d("ClockWeatherApp", "onAppWidgetOptionsChanged for ${this::class.simpleName}, id=$appWidgetId")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                val updater = getUpdater(context, appWidgetManager, entryPoint)
                updater.updateWidget(appWidgetId)
            } catch (e: Throwable) {
                Log.e("ClockWeatherApp", "onAppWidgetOptionsChanged failed for ${this@BaseWidgetProvider::class.simpleName}", e)
                try {
                    val fallback = RemoteViews(context.packageName, fallbackLayoutResId)
                    appWidgetManager.updateAppWidget(appWidgetId, fallback)
                } catch (inner: Throwable) {
                    Log.e("ClockWeatherApp", "Fallback also failed for widget $appWidgetId", inner)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
