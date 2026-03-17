package com.clockweather.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import com.clockweather.app.receiver.ClockAlarmReceiver
import com.clockweather.app.presentation.settings.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import com.clockweather.app.util.dataStore
import javax.inject.Inject

@HiltAndroidApp
class ClockWeatherApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        restoreLanguageSetting()

        if (ClockAlarmReceiver.hasAnyActiveWidgets(this)) {
            refreshAllWidgets(this, isClockTick = false)
            ClockAlarmReceiver.scheduleNextTick(this)
        }
    }

    /**
     * Internal helper to refresh all active widgets.
     * @param isClockTick If true, it performs a partial 'incremental' clock update (animations enabled).
     *                    If false, it performs a full widget update.
     */
    fun refreshAllWidgets(context: Context, isClockTick: Boolean) {
        android.util.Log.d("ClockWeatherApp", "Refreshing all widgets. isClockTick=$isClockTick")
        val mgr = AppWidgetManager.getInstance(context)
        try {
            val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            
            val providers = listOf(
                CompactWidgetProvider(),
                ExtendedWidgetProvider(),
                ForecastWidgetProvider(),
                LargeWidgetProvider()
            )

            providers.forEach { provider ->
                val component = ComponentName(context, provider::class.java)
                val ids = mgr.getAppWidgetIds(component)
                android.util.Log.d("ClockWeatherApp", "Checking provider ${component.shortClassName}: found ${ids.size} IDs")
                if (ids.isNotEmpty()) {
                    val updater = provider.getUpdater(context.applicationContext, mgr, entryPoint)
                    ids.forEach { id ->
                        if (isClockTick) updater.updateClockOnly(id)
                        else updater.updateWidget(id)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ClockWeatherApp", "Failed to refresh widgets", e)
        }
    }

    private fun restoreLanguageSetting() {
        try {
            // Use the shared delegate accessor (guarantees single instance)
            val languageCode = kotlin.runCatching {
                runBlocking {
                    dataStore.data
                        .map { it[SettingsViewModel.KEY_LANGUAGE] ?: "system" }
                        .first()
                }
            }.getOrDefault("system")

            val locale = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(locale)
        } catch (_: Exception) { }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
