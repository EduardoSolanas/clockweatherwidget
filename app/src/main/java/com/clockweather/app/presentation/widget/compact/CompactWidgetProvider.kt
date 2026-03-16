package com.clockweather.app.presentation.widget.compact

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.common.BaseWidgetProvider
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater

class CompactWidgetProvider : BaseWidgetProvider() {
    override fun getUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint
    ): BaseWidgetUpdater {
        return CompactWidgetUpdater(context, appWidgetManager, entryPoint)
    }
}
