package com.clockweather.app.presentation.widget.common

import android.content.Context

object WidgetClockStateStore {
    private const val PREFS_NAME = "widget_clock_state"
    private const val KEY_PREFIX = "last_rendered_epoch_minute_"
    private const val KEY_BASELINE_PREFIX = "baseline_ready_"

    fun getLastRenderedEpochMinute(context: Context, appWidgetId: Int): Long? {
        val prefs = prefs(context)
        val key = key(appWidgetId)
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun markRendered(context: Context, appWidgetId: Int, epochMinute: Long) {
        prefs(context).edit().putLong(key(appWidgetId), epochMinute).apply()
    }

    fun clearWidget(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(key(appWidgetId))
            .remove(baselineKey(appWidgetId))
            .apply()
    }

    fun isBaselineReady(context: Context, appWidgetId: Int): Boolean {
        return prefs(context).getBoolean(baselineKey(appWidgetId), false)
    }

    fun markBaselineReady(context: Context, appWidgetId: Int) {
        prefs(context).edit().putBoolean(baselineKey(appWidgetId), true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "$KEY_PREFIX$appWidgetId"
    private fun baselineKey(appWidgetId: Int) = "$KEY_BASELINE_PREFIX$appWidgetId"
}
