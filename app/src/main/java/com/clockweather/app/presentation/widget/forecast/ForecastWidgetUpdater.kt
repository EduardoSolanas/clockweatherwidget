package com.clockweather.app.presentation.widget.forecast

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater
import com.clockweather.app.presentation.widget.common.WidgetDataBinder

class ForecastWidgetUpdater(
    context: Context,
    appWidgetManager: AppWidgetManager,
    entryPoint: WidgetEntryPoint
) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {

    override val layoutResId = R.layout.widget_forecast
    override val rootViewId = R.id.widget_root
    override val dateViewId = R.id.widget_date

    override fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences) {
        WidgetDataBinder.bindWeeklyForecastRows(context, views, weather, tempUnit)
    }
}
