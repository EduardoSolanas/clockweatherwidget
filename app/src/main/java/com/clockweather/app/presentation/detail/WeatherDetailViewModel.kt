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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
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

    internal var lastRefreshTimeMs: Long = 0L

    /** Observes the temperature unit from DataStore — updates immediately when changed in Settings. */
    val temperatureUnit: StateFlow<TemperatureUnit> = dataStore.data
        .map { prefs ->
            val name = prefs[stringPreferencesKey("temperature_unit")] ?: TemperatureUnit.CELSIUS.name
            runCatching { TemperatureUnit.valueOf(name) }.getOrDefault(TemperatureUnit.CELSIUS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.CELSIUS)

    val forecastDays: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[com.clockweather.app.presentation.settings.SettingsViewModel.KEY_FORECAST_DAYS] ?: 7 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    // ── Widget reliability setup prompts ─────────────────────────────────────
    // Exposed so the UI can show a setup banner until both are granted.
    private val _needsBatteryExemption = MutableStateFlow(!checkBatteryOptimizationExempt())
    val needsBatteryExemption: StateFlow<Boolean> = _needsBatteryExemption.asStateFlow()

    /** Call from ON_RESUME after the user returns from system settings. */
    fun refreshPermissions() {
        _needsBatteryExemption.value = !checkBatteryOptimizationExempt()
    }

    private fun checkBatteryOptimizationExempt(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    init {
        loadWeather()
        observeForecastDaysChanges()
        observeWeatherProviderChanges()
    }

    /**
     * Re-fetches weather from the network whenever the user changes the forecast-days
     * setting. drop(1) skips the initial emission so loadWeather() isn't called twice.
     */
    private fun observeForecastDaysChanges() {
        viewModelScope.launch {
            forecastDays
                .drop(1) // first emission already handled by loadWeather()
                .collect { days ->
                    val location = (uiState.value as? UiState.Success)?.data?.location
                        ?: return@collect
                    runCatching { refreshWeatherAndWidgets(location, days) }
                }
        }
    }

    /**
     * Re-fetches weather whenever the backend provider changes so the detail screen
     * does not keep showing stale cached data from the previously selected provider.
     */
    private fun observeWeatherProviderChanges() {
        viewModelScope.launch {
            dataStore.data
                .map { prefs -> prefs[com.clockweather.app.presentation.settings.SettingsViewModel.KEY_WEATHER_PROVIDER] }
                .drop(1)
                .collect {
                    val location = (uiState.value as? UiState.Success)?.data?.location
                    if (location == null) {
                        loadWeather()
                    } else {
                        runCatching { refreshWeatherAndWidgets(location, forecastDays.value) }
                    }
                }
        }
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
                    refreshWeatherAndWidgets(location, forecastDays.value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Network failed — continue with cache
                }

                getWeatherDataUseCase(location)
                    .catch { e ->
                        if (e is CancellationException) throw e
                        _uiState.value = UiState.Error(e.message ?: context.getString(R.string.error_no_data))
                    }
                    .collect { weatherData ->
                        _uiState.value = UiState.Success(weatherData)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: context.getString(R.string.error_no_data))
            }
        }
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (lastRefreshTimeMs > 0L && now - lastRefreshTimeMs < REFRESH_THROTTLE_MS) return
        lastRefreshTimeMs = now
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
                    refreshWeatherAndWidgets(resolvedLocation, forecastDays.value)
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun refreshWeatherAndWidgets(
        location: com.clockweather.app.domain.model.Location,
        forecastDays: Int
    ) {
        refreshWeatherUseCase(location, forecastDays = forecastDays)
        val app = context.applicationContext as? com.clockweather.app.ClockWeatherApplication
        app?.refreshAllWidgets(app)
    }

    companion object {
        const val REFRESH_THROTTLE_MS = 5 * 60 * 1000L
    }
}
