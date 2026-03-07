package com.clockweather.app.presentation.detail

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class WeatherDetailViewModel @Inject constructor(
    private val getWeatherDataUseCase: GetWeatherDataUseCase,
    private val refreshWeatherUseCase: RefreshWeatherUseCase,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<WeatherData>>(UiState.Loading)
    val uiState: StateFlow<UiState<WeatherData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(TemperatureUnit.CELSIUS)
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    init {
        loadWeather()
    }

    private fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                var locations = locationRepository.getSavedLocations().first()

                // No saved location → try GPS (6s timeout) then London fallback
                if (locations.isEmpty()) {
                    val detected = withTimeoutOrNull(6_000L) {
                        locationRepository.getCurrentLocation()
                    }
                    val loc = detected
                        ?: com.clockweather.app.data.repository.LocationRepositoryImpl.FALLBACK_LOCATION
                    locationRepository.saveLocation(loc)
                    locations = locationRepository.getSavedLocations().first()
                }

                val location = locations.first()

                // Always fetch fresh data from network first so the cache is never empty
                try {
                    refreshWeatherUseCase(location)
                } catch (e: Exception) {
                    // Network failed — continue, will show whatever is in cache
                }

                // Now collect from DB (will always have data after refresh above)
                getWeatherDataUseCase(location)
                    .catch { e ->
                        _uiState.value = UiState.Error(e.message ?: "Failed to load weather")
                    }
                    .collect { weatherData ->
                        _uiState.value = UiState.Success(weatherData)
                    }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unexpected error")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val locations = locationRepository.getSavedLocations().first()
                val location = locations.firstOrNull() ?: return@launch
                refreshWeatherUseCase(location)
            } catch (e: Exception) {
                // ignore
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        _temperatureUnit.value = unit
    }
}
