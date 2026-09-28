package com.clockweather.app.presentation.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The ad decision used to read `SettingsViewModel.isTesterMode`, a StateFlow whose placeholder is
 * `false` until DataStore emits. The launch-time effect read that placeholder once, so a tester
 * with the switch on was still shown an interstitial. The decision must come from the stored value.
 */
class TesterModeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun newDataStore() =
        PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb").also { it.delete() } }

    @Test
    fun `a stored tester switch is reported as enabled on the first read`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[SettingsViewModel.KEY_TESTER_MODE] = true }

        assertTrue(TesterMode.isEnabled(dataStore))
    }

    @Test
    fun `tester mode is off when the switch was never set`() = runTest {
        assertFalse(TesterMode.isEnabled(newDataStore()))
    }

    @Test
    fun `tester mode is off when the switch is stored as off`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[SettingsViewModel.KEY_TESTER_MODE] = false }

        assertFalse(TesterMode.isEnabled(dataStore))
    }
}
