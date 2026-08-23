package com.clockweather.app.presentation.widget.extended

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater
import com.clockweather.app.presentation.widget.common.WeatherIconMapper
import com.clockweather.app.presentation.widget.common.WidgetDataBinder

class ExtendedWidgetUpdater(
    context: Context,
    appWidgetManager: AppWidgetManager,
    entryPoint: WidgetEntryPoint
) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {

    override val layoutResId = R.layout.widget_extended
    override val rootViewId = R.id.widget_root
    override val dateViewId = R.id.widget_date
    override val widgetPaddingDp = 10f
    override val hasForecastViews = true

    // Deliberately empty. A breakpoint map parcels every mapped view in one
    // transaction, so it only pays off if smaller sizes bind less content — and the
    // only content worth dropping here is the five-day forecast row, which is the
    // reason these widgets exist. Smoother resize animation is not worth removing it.
    override fun getResponsiveSizeBreakpoints(): List<SizeF> = emptyList()

    override fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences) {
        val showToday = prefs[booleanPreferencesKey("show_today_extended")] ?: false
        val iconStyle = WeatherIconMapper.fromPreferenceValue(
            prefs[SettingsViewModel.KEY_WEATHER_ICON_STYLE] ?: SettingsViewModel.ICON_STYLE_GLASS
        )
        views.setViewVisibility(R.id.weather_card, if (showToday) View.VISIBLE else View.GONE)
        WidgetDataBinder.bindWeeklyForecastRows(context, views, weather, tempUnit, iconStyle = iconStyle)
    }
}
