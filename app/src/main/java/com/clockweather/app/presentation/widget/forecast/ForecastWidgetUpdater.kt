package com.clockweather.app.presentation.widget.forecast

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.datastore.preferences.core.Preferences
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.presentation.widget.common.BaseWidgetUpdater
import com.clockweather.app.presentation.widget.common.WidgetSizeClass
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

    // Two breakpoints, not three: at CLAY_3D a third full-content view would
    // exceed the transaction budget. See WidgetPayloadBudgetTest.
    override fun getResponsiveSizeBreakpoints(): List<SizeF> {
        return if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                SizeF(180f, 120f),
                SizeF(350f, 220f),
            )
        } else emptyList()
    }

    override fun bindExtra(
        views: RemoteViews,
        weather: WeatherData,
        tempUnit: TemperatureUnit,
        prefs: Preferences,
        sizeClass: WidgetSizeClass,
    ) {
        val iconStyle = WeatherIconMapper.fromPreferenceValue(
            prefs[SettingsViewModel.KEY_WEATHER_ICON_STYLE] ?: SettingsViewModel.ICON_STYLE_GLASS
        )
        bindForecastRowForSize(views, weather, tempUnit, iconStyle, sizeClass)
    }
}
