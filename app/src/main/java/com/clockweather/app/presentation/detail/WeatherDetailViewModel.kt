package com.clockweather.app.presentation.detail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.usecase.GetWeatherDataUseCase
import com.clockweather.app.domain.usecase.RefreshWeatherUseCase
import com.clockweather.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import com.clockweather.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class WeatherDetailViewModel @Inject constructor(
    private val getWeatherDataUseCase: GetWeatherDataUseCase,
    private val refreshWeatherUseCase: RefreshWeatherUseCase,
    private val locationRepository: LocationRepository,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<WeatherData>>(UiState.Loading)
    val uiState: StateFlow<UiState<WeatherData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private var weatherLoadJob: Job? = null

    /** Observes the temperature unit from DataStore — updates immediately when changed in Settings. */
    val temperatureUnit: StateFlow<TemperatureUnit> = dataStore.data
        .map { prefs ->
            val name = prefs[stringPreferencesKey("temperature_unit")] ?: TemperatureUnit.CELSIUS.name
            runCatching { TemperatureUnit.valueOf(name) }.getOrDefault(TemperatureUnit.CELSIUS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.CELSIUS)

    init {
        loadWeather()
    }

    private fun loadWeather() {
        weatherLoadJob?.cancel()
        weatherLoadJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                var locations = locationRepository.getSavedLocations().first()

                if (locations.isEmpty()) {
                    val detected = withTimeoutOrNull(6_000L) {
                        locationRepository.getCurrentLocation()
                    }
                    val loc = detected ?: locationRepository.getFallbackLocation()
                    locationRepository.saveLocation(loc)
                    locations = locationRepository.getSavedLocations().first()
                }

                val location = locations.first()

                try {
                    refreshWeatherUseCase(location)
                } catch (e: Exception) {
                    // Network failed — continue with cache
                }

                getWeatherDataUseCase(location)
                    .catch { e ->
                        _uiState.value = UiState.Error(e.message ?: context.getString(R.string.error_no_data))
                    }
                    .collect { weatherData ->
                        _uiState.value = UiState.Success(weatherData)
                    }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: context.getString(R.string.error_no_data))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val savedLocations = locationRepository.getSavedLocations().first()
                val currentUiLocation = (uiState.value as? UiState.Success)?.data?.location
                val primaryLocation = savedLocations.firstOrNull() ?: currentUiLocation

                val latestCurrentLocation = withTimeoutOrNull(6_000L) {
                    locationRepository.getCurrentLocation()
                }

                val shouldTrackCurrentLocation = primaryLocation?.isCurrentLocation == true || primaryLocation == null

                val resolvedLocation = when {
                    shouldTrackCurrentLocation && latestCurrentLocation != null -> {
                        val reusedId = primaryLocation?.id ?: 0L
                        val candidate = latestCurrentLocation.copy(
                            id = reusedId,
                            isCurrentLocation = true
                        )
                        val insertedId = locationRepository.saveLocation(candidate)
                        val finalId = if (candidate.id == 0L) insertedId else candidate.id
                        candidate.copy(id = finalId)
                    }
                    primaryLocation != null -> primaryLocation
                    latestCurrentLocation != null -> {
                        val insertedId = locationRepository.saveLocation(
                            latestCurrentLocation.copy(isCurrentLocation = true)
                        )
                        latestCurrentLocation.copy(id = insertedId, isCurrentLocation = true)
                    }
                    else -> {
                        val fallback = locationRepository.getFallbackLocation()
                        val insertedId = locationRepository.saveLocation(fallback)
                        fallback.copy(id = insertedId)
                    }
                }

                if (currentUiLocation == null || currentUiLocation.id != resolvedLocation.id) {
                    // Location context changed (or we had no active context): restart the load stream.
                    loadWeather()
                } else {
                    refreshWeatherUseCase(resolvedLocation)
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
