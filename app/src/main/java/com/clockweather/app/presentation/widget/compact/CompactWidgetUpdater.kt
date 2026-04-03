package com.clockweather.app.presentation.widget.compact

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater

class CompactWidgetUpdater(
    context: Context,
    appWidgetManager: AppWidgetManager,
    entryPoint: WidgetEntryPoint
) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {

    override val layoutResId = R.layout.widget_compact
    override val rootViewId = R.id.widget_root
    override val dateViewId = R.id.widget_date
    override val usesSimpleClockDigits = true
    override val forceStaticClockRendering = true

    override fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences) {
        val showToday = prefs[booleanPreferencesKey("show_today_compact")] ?: true
        views.setViewVisibility(R.id.weather_card, if (showToday) View.VISIBLE else View.GONE)
    }
}
