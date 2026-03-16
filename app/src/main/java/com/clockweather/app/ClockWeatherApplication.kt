package com.clockweather.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.clockweather.app.presentation.settings.SettingsViewModel
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
        // Restore saved language setting before any Activity launches
        restoreLanguageSetting()
    }

    private fun restoreLanguageSetting() {
        try {
            // Use a temporary scope so we don't conflict with Hilt's singleton DataStore.
            // We just need a one-shot read before any Activity starts.
            val tempScope = CoroutineScope(Dispatchers.IO)
            val tempStore = PreferenceDataStoreFactory.create(scope = tempScope) {
                File(filesDir, "datastore/settings.preferences_pb")
            }
            val languageCode = runBlocking {
                tempStore.data
                    .map { prefs -> prefs[SettingsViewModel.KEY_LANGUAGE] ?: "system" }
                    .first()
            }
            tempScope.cancel() // cancel immediately after read
            val locale = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(locale)
        } catch (_: Exception) {
            // First launch or read failure — leave system locale
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
