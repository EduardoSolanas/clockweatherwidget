package com.clockweather.app.presentation.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.SpeedUnit
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val KEY_TEMP_UNIT = stringPreferencesKey("temperature_unit")
        val KEY_SPEED_UNIT = stringPreferencesKey("speed_unit")
        val KEY_UPDATE_INTERVAL = intPreferencesKey("update_interval_minutes")
        val KEY_USE_24H = booleanPreferencesKey("use_24h_clock")
        val KEY_SHOW_DATE = booleanPreferencesKey("show_date_in_widget")
        val KEY_SHOW_TODAY_COMPACT = booleanPreferencesKey("show_today_compact")
        val KEY_SHOW_TODAY_EXTENDED = booleanPreferencesKey("show_today_extended")
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

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_TEMP_UNIT] = unit.name }
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SPEED_UNIT] = unit.name }
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
        }
    }

    fun setShowDateInWidget(show: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_SHOW_DATE] = show } }
    }

    fun setShowTodayCompact(show: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_SHOW_TODAY_COMPACT] = show } }
    }

    fun setShowTodayExtended(show: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_SHOW_TODAY_EXTENDED] = show } }
    }

    fun deleteLocation(locationId: Long) {
        viewModelScope.launch {
            locationRepository.deleteLocation(locationId)
        }
    }
}

