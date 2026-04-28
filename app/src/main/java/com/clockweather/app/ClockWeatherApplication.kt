package com.clockweather.app

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider
import com.clockweather.app.presentation.widget.large.LargeWidgetProvider
import com.clockweather.app.util.WidgetPrefsCache
import com.clockweather.app.util.dataStore
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class ClockWeatherApplication : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(Dispatchers.Default)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        restoreLanguageSetting()
        runBlocking { recoverClockThemeAfterBrokenMigration() }
        // Initialise the in-memory preference cache so widget updates skip disk I/O.
        WidgetPrefsCache.init(dataStore, appScope)
        WidgetPrefsCache.seedBlocking(dataStore)
    }

    private suspend fun recoverClockThemeAfterBrokenMigration() {
        val brokenMigrationKey = booleanPreferencesKey("clock_theme_mapping_migrated_v1")
        val recoveryMigrationKey = booleanPreferencesKey("clock_theme_mapping_migrated_v2")
        val clockThemeKey = stringPreferencesKey("clock_theme")

        dataStore.edit { prefs ->
            if (prefs[recoveryMigrationKey] == true) return@edit

            val currentTheme = prefs[clockThemeKey]
            if (prefs[brokenMigrationKey] == true && currentTheme == SettingsViewModel.CLOCK_THEME_DARK) {
                prefs[clockThemeKey] = SettingsViewModel.CLOCK_THEME_LIGHT
            } else if (prefs[brokenMigrationKey] != true && currentTheme == SettingsViewModel.CLOCK_THEME_DARK) {
                // Pre-fix installs often persisted "dark" while visually showing the light style.
                // Normalize that legacy value to the intended light theme once.
                prefs[clockThemeKey] = SettingsViewModel.CLOCK_THEME_LIGHT
            }

            prefs[recoveryMigrationKey] = true
        }
    }

    /**
     * Refreshes all active widgets with a full data update.
     * Called by receivers that respond to timezone/date changes and package replacement.
     */
    suspend fun refreshAllWidgets(context: Context) {
        android.util.Log.d("ClockWeatherApp", "Refreshing all widgets")
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
                    ids.forEach { id -> updater.updateWidget(id) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ClockWeatherApp", "Failed to refresh widgets", e)
        }
    }

    private fun restoreLanguageSetting() {
        try {
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
