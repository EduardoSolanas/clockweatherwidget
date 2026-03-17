package com.clockweather.app.presentation.settings

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.SpeedUnit
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.ClockTileSize
import com.clockweather.app.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        val KEY_TEMP_UNIT = stringPreferencesKey("temperature_unit")
        val KEY_SPEED_UNIT = stringPreferencesKey("speed_unit")
        val KEY_UPDATE_INTERVAL = intPreferencesKey("update_interval_minutes")
        val KEY_USE_24H = booleanPreferencesKey("use_24h_clock")
        val KEY_SHOW_DATE = booleanPreferencesKey("show_date_in_widget")
        val KEY_SHOW_TODAY_COMPACT = booleanPreferencesKey("show_today_compact")
        val KEY_SHOW_TODAY_EXTENDED = booleanPreferencesKey("show_today_extended")
        val KEY_DATE_FONT_SIZE = floatPreferencesKey("date_font_size_sp")
        val KEY_CLOCK_THEME = stringPreferencesKey("clock_theme")
        val KEY_CLOCK_TILE_SIZE = stringPreferencesKey("clock_tile_size")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        const val DEFAULT_DATE_FONT_SP = 15f
        const val CLOCK_THEME_DARK = "dark"
        const val CLOCK_THEME_LIGHT = "light"
    }

    val savedLocations: StateFlow<List<Location>> = locationRepository.getSavedLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val temperatureUnit: StateFlow<TemperatureUnit> = dataStore.data
        .map { prefs ->
            val name = prefs[KEY_TEMP_UNIT] ?: TemperatureUnit.CELSIUS.name
            runCatching { TemperatureUnit.valueOf(name) }.getOrDefault(TemperatureUnit.CELSIUS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.CELSIUS)

    val speedUnit: StateFlow<SpeedUnit> = dataStore.data
        .map { prefs ->
            val name = prefs[KEY_SPEED_UNIT] ?: SpeedUnit.KMH.name
            runCatching { SpeedUnit.valueOf(name) }.getOrDefault(SpeedUnit.KMH)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpeedUnit.KMH)

    val updateIntervalMinutes: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[KEY_UPDATE_INTERVAL] ?: 30 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val use24hClock: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_USE_24H] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showDateInWidget: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SHOW_DATE] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showTodayCompact: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SHOW_TODAY_COMPACT] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showTodayExtended: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SHOW_TODAY_EXTENDED] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dateFontSizeSp: StateFlow<Float> = dataStore.data
        .map { prefs -> prefs[KEY_DATE_FONT_SIZE] ?: DEFAULT_DATE_FONT_SP }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DATE_FONT_SP)

    val clockTheme: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_CLOCK_THEME] ?: CLOCK_THEME_DARK }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CLOCK_THEME_DARK)

    val clockTileSize: StateFlow<ClockTileSize> = dataStore.data
        .map { prefs ->
            val name = prefs[KEY_CLOCK_TILE_SIZE] ?: ClockTileSize.MEDIUM.name
            runCatching { ClockTileSize.valueOf(name) }
                .getOrDefault(ClockTileSize.MEDIUM)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClockTileSize.MEDIUM)

    val selectedLanguage: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_LANGUAGE] ?: "system" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    // ── Setters (all trigger a widget redraw) ──────────────────────────────────

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_LANGUAGE] = languageCode }
            
            // Apply language immediately
            val appLocale = if (languageCode == "system") {
                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
            } else {
                androidx.core.os.LocaleListCompat.forLanguageTags(languageCode)
            }
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_TEMP_UNIT] = unit.name }
            triggerWidgetUpdate()
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SPEED_UNIT] = unit.name }
            triggerWidgetUpdate()
        }
    }

    fun setUpdateInterval(minutes: Int) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_UPDATE_INTERVAL] = minutes }
        }
    }

    fun set24hClock(use24h: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_USE_24H] = use24h }
            triggerWidgetUpdate()
        }
    }

    fun setShowDateInWidget(show: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SHOW_DATE] = show }
            triggerWidgetUpdate()
        }
    }

    fun setShowTodayCompact(show: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SHOW_TODAY_COMPACT] = show }
            triggerWidgetUpdate()
        }
    }

    fun setShowTodayExtended(show: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SHOW_TODAY_EXTENDED] = show }
            triggerWidgetUpdate()
        }
    }

    fun setDateFontSize(sp: Float) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_DATE_FONT_SIZE] = sp }
            triggerWidgetUpdate()
        }
    }

    fun setClockTheme(theme: String) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_CLOCK_THEME] = theme }
            triggerWidgetUpdate()
        }
    }

    fun setClockTileSize(size: ClockTileSize) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_CLOCK_TILE_SIZE] = size.name }
            triggerWidgetUpdate()
        }
    }

    fun deleteLocation(locationId: Long) {
        viewModelScope.launch {
            locationRepository.deleteLocation(locationId)
        }
    }

    // ── Private ────────────────────────────────────────────────────────────────

    /**
     * Kicks all active widgets to re-draw immediately after a pref change.
     */
    private fun triggerWidgetUpdate() {
        val app = context.applicationContext as? ClockWeatherApplication
        app?.refreshAllWidgets(context, isClockTick = false)
    }
}
