package com.clockweather.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import com.clockweather.app.presentation.settings.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class ClockWeatherApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        restoreLanguageSetting()

        // Centralized time tick handler
        val timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_TIME_TICK) return
                context?.let { refreshAllWidgets(it, isClockTick = true) }
            }
        }
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    /**
     * Internal helper to refresh all active widgets.
     * @param isClockTick If true, it performs a partial 'incremental' clock update (animations enabled).
     *                    If false, it performs a full widget update.
     */
    fun refreshAllWidgets(context: Context, isClockTick: Boolean) {
        val mgr = AppWidgetManager.getInstance(context)
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        
        val providers = listOf(
            CompactWidgetProvider(),
            ExtendedWidgetProvider(),
            ForecastWidgetProvider(),
            LargeWidgetProvider()
        )

        providers.forEach { provider ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, provider::class.java))
            if (ids.isNotEmpty()) {
                val updater = provider.getUpdater(context, mgr, entryPoint)
                ids.forEach { id ->
                    if (isClockTick) updater.updateClockOnly(id)
                    else updater.updateWidget(id)
                }
            }
        }
    }

    private fun restoreLanguageSetting() {
        try {
            val tempScope = CoroutineScope(Dispatchers.IO)
            val tempStore = PreferenceDataStoreFactory.create(scope = tempScope) {
                File(filesDir, "datastore/settings.preferences_pb")
            }
            val languageCode = runBlocking {
                tempStore.data
                    .map { prefs -> prefs[SettingsViewModel.KEY_LANGUAGE] ?: "system" }
                    .first()
            }
            tempScope.cancel() 
            val locale = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(locale)
        } catch (_: Exception) {
            // First launch or read failure
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
