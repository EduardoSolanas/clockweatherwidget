package com.clockweather.app.presentation.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first

/**
 * Reads the stored tester switch for one-shot ad decisions.
 *
 * `SettingsViewModel.isTesterMode` starts as `false` until DataStore emits, so a decision made
 * from its first value treats every tester as an ordinary user. This suspends until the real
 * value is loaded instead.
 */
object TesterMode {
    suspend fun isEnabled(dataStore: DataStore<Preferences>): Boolean =
        dataStore.data.first()[SettingsViewModel.KEY_TESTER_MODE] ?: false
}
