package com.clockweather.app.presentation.widget.forecast

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater
import com.clockweather.app.presentation.widget.common.WeatherIconMapper
import com.clockweather.app.presentation.widget.common.WidgetDataBinder

class ForecastWidgetUpdater(
    context: Context,
    appWidgetManager: AppWidgetManager,
    entryPoint: WidgetEntryPoint
) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {

    override val layoutResId = R.layout.widget_forecast
    override val rootViewId = R.id.widget_root
    override val dateViewId = R.id.widget_date
    override val minimumFutureForecastDaysRequired = 7
    override val widgetPaddingDp = 10f
    override val hasForecastViews = true

    // Deliberately empty. A breakpoint map parcels every mapped view in one
    // transaction, so it only pays off if smaller sizes bind less content — and the
    // only content worth dropping here is the five-day forecast row, which is the
    // reason these widgets exist. Smoother resize animation is not worth removing it.
    override fun getResponsiveSizeBreakpoints(): List<SizeF> = emptyList()

    override fun bindExtra(views: RemoteViews, weather: WeatherData, tempUnit: TemperatureUnit, prefs: Preferences) {
        val iconStyle = WeatherIconMapper.fromPreferenceValue(
            prefs[SettingsViewModel.KEY_WEATHER_ICON_STYLE] ?: SettingsViewModel.ICON_STYLE_GLASS
        )
        WidgetDataBinder.bindWeeklyForecastRows(context, views, weather, tempUnit, iconStyle = iconStyle)
    }
}
