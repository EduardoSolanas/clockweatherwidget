package com.clockweather.app.presentation.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollenInWidgetDefaultTest {

    @Test
    fun `pollen stays off in the widget until the user turns it on`() {
        assertFalse(SettingsViewModel.isPollenShownInWidget(emptyPreferences()))
    }

    @Test
    fun `an explicit choice overrides the default`() {
        val on = mutablePreferencesOf().apply { this[SettingsViewModel.KEY_SHOW_POLLEN_IN_WIDGET] = true }
        val off = mutablePreferencesOf().apply { this[SettingsViewModel.KEY_SHOW_POLLEN_IN_WIDGET] = false }

        assertTrue(SettingsViewModel.isPollenShownInWidget(on))
        assertFalse(SettingsViewModel.isPollenShownInWidget(off))
    }
}
