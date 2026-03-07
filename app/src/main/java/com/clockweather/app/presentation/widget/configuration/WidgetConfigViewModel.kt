package com.clockweather.app.presentation.widget.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.usecase.GetCurrentLocationUseCase
import com.clockweather.app.domain.usecase.SearchLocationUseCase
import com.clockweather.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val searchLocationUseCase: SearchLocationUseCase,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase
) : ViewModel() {

    private var widgetId: Int = -1

    private val _searchResults = MutableStateFlow<UiState<List<Location>>>(UiState.Success(emptyList()))
    val searchResults: StateFlow<UiState<List<Location>>> = _searchResults.asStateFlow()

    private val _selectedLocation = MutableStateFlow<Location?>(null)
    val selectedLocation: StateFlow<Location?> = _selectedLocation.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(TemperatureUnit.CELSIUS)
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setWidgetId(id: Int) { widgetId = id }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) searchLocations(query)
    }

    fun searchLocations(query: String) {
        viewModelScope.launch {
            _searchResults.value = UiState.Loading
            try {
                val results = searchLocationUseCase(query)
                _searchResults.value = UiState.Success(results)
            } catch (e: Exception) {
                _searchResults.value = UiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun selectLocation(location: Location) {
        _selectedLocation.value = location
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _searchResults.value = UiState.Loading
            try {
                val location = getCurrentLocationUseCase()
                if (location != null) {
                    _selectedLocation.value = location
                    _searchResults.value = UiState.Success(emptyList())
                } else {
                    _searchResults.value = UiState.Error("Could not get current location")
                }
            } catch (e: Exception) {
                _searchResults.value = UiState.Error(e.message ?: "Location error")
            }
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        _temperatureUnit.value = unit
    }
}

