package com.clockweather.app.presentation.widget.extended

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.common.BaseWidgetProvider
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater

class ExtendedWidgetProvider : BaseWidgetProvider() {
    override val fallbackLayoutResId = com.clockweather.app.R.layout.widget_extended

    override fun getUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint
    ): BaseWidgetUpdater {
        return ExtendedWidgetUpdater(context, appWidgetManager, entryPoint)
    }
}
