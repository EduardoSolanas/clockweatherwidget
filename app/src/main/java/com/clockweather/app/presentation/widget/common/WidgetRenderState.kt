package com.clockweather.app.presentation.widget.common

import android.content.Context

/**
 * Records which widgets have had real content published to them.
 *
 * [BaseWidgetProvider] pushes a placeholder layout before it reads the cache, because a widget
 * that has never been given any RemoteViews shows "Can't load widget" on some launchers while
 * the process starts. For a widget that is already displaying weather that placeholder is
 * destructive: it blanks the widget on every routine callback.
 *
 * The launcher keeps rendering the last RemoteViews it was given long after the sending process
 * dies, so this record has to be persistent. An in-memory flag would report "nothing rendered"
 * after every process restart and blank exactly the widgets it is meant to protect.
 *
 * SharedPreferences rather than the app's DataStore: [android.appwidget.AppWidgetProvider] is a
 * broadcast receiver and this decision is made before the update coroutine starts, so the read
 * has to be synchronous.
 */
internal object WidgetRenderState {

    const val PREFERENCES_NAME = "widget_render_state"

    private fun key(appWidgetId: Int) = "rendered_$appWidgetId"

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** True when the widget has nothing worth preserving and should be given the placeholder. */
    fun needsPlaceholder(context: Context, appWidgetId: Int): Boolean =
        !preferences(context).getBoolean(key(appWidgetId), false)

    fun markRendered(context: Context, appWidgetId: Int) {
        preferences(context).edit().putBoolean(key(appWidgetId), true).apply()
    }

    /** Widget ids are reused, so a removed widget must not inherit the previous one's record. */
    fun clear(context: Context, appWidgetId: Int) {
        preferences(context).edit().remove(key(appWidgetId)).apply()
    }
}
