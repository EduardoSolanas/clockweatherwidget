package com.clockweather.app.presentation.widget.extended

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
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
import com.clockweather.app.presentation.widget.common.WidgetSizeClass
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

    // Below 150dp tall the forecast row is dropped rather than merely hidden,
    // which is what keeps the summed payload inside the transaction budget.
    override fun getResponsiveSizeBreakpoints(): List<SizeF> {
        return if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                SizeF(400f, 140f),
                SizeF(520f, 180f),
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
        val showToday = prefs[booleanPreferencesKey("show_today_extended")] ?: false
        val iconStyle = WeatherIconMapper.fromPreferenceValue(
            prefs[SettingsViewModel.KEY_WEATHER_ICON_STYLE] ?: SettingsViewModel.ICON_STYLE_GLASS
        )
        views.setViewVisibility(R.id.weather_card, if (showToday) View.VISIBLE else View.GONE)
        bindForecastRowForSize(views, weather, tempUnit, iconStyle, sizeClass)
    }
}
