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
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.SpeedUnit
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.ClockTileSize
import com.clockweather.app.domain.model.WeatherProviderType
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        val KEY_TEMP_UNIT = stringPreferencesKey("temperature_unit")
        val KEY_WEATHER_PROVIDER = WeatherProviderPreferences.KEY_WEATHER_PROVIDER
        val KEY_SPEED_UNIT = stringPreferencesKey("speed_unit")
        val KEY_UPDATE_INTERVAL = intPreferencesKey("update_interval_minutes")
        val KEY_WEATHER_REFRESH_INTERVAL = intPreferencesKey("weather_refresh_interval_minutes")
        val KEY_USE_24H = booleanPreferencesKey("use_24h_clock")
        val KEY_SHOW_DATE = booleanPreferencesKey("show_date_in_widget")
        val KEY_SHOW_TODAY_COMPACT = booleanPreferencesKey("show_today_compact")
        val KEY_SHOW_TODAY_EXTENDED = booleanPreferencesKey("show_today_extended")
        val KEY_DATE_FONT_SIZE = floatPreferencesKey("date_font_size_sp")
        val KEY_CLOCK_THEME = stringPreferencesKey("clock_theme")
        val KEY_CLOCK_TILE_SIZE = stringPreferencesKey("clock_tile_size")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_HIGH_PRECISION = booleanPreferencesKey("high_precision_clock")
        val KEY_FLIP_ANIMATION = booleanPreferencesKey("flip_animation_enabled")
        val KEY_FORECAST_DAYS = intPreferencesKey("forecast_days")
        const val DEFAULT_DATE_FONT_SP = 15f
        const val DEFAULT_WEATHER_REFRESH_INTERVAL_MINUTES = 30
        const val CLOCK_THEME_DARK = "dark"
        const val CLOCK_THEME_LIGHT = "light"

        /**
         * Returns 14 for screens >=600dp wide (tablets / foldables in landscape),
         * 7 for phones. Used as the first-run default when no preference is stored.
         */
        fun smartDefaultForecastDays(screenWidthDp: Int): Int = if (screenWidthDp >= 600) 14 else 7

        fun normalizeForecastDaysForProvider(
            requestedDays: Int,
            provider: WeatherProviderType
        ): Int = provider.supportedForecastDays
            .filter { it <= requestedDays }
            .maxOrNull()
            ?: provider.supportedForecastDays.first()
    }

    init {
        // Ensure the stored forecast range always matches the currently selected provider.
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val provider = WeatherProviderPreferences.resolve(prefs[KEY_WEATHER_PROVIDER])
            val widthDp = context.resources.configuration.screenWidthDp
            val requestedDays = prefs[KEY_FORECAST_DAYS] ?: smartDefaultForecastDays(widthDp)
            val normalizedDays = normalizeForecastDaysForProvider(requestedDays, provider)
            if (prefs[KEY_FORECAST_DAYS] != normalizedDays) {
                dataStore.edit { it[KEY_FORECAST_DAYS] = normalizedDays }
            }
        }
    }

    val savedLocations: StateFlow<List<Location>> = locationRepository.getSavedLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val temperatureUnit: StateFlow<TemperatureUnit> = dataStore.data
        .map { prefs ->
            val name = prefs[KEY_TEMP_UNIT] ?: TemperatureUnit.CELSIUS.name
            runCatching { TemperatureUnit.valueOf(name) }.getOrDefault(TemperatureUnit.CELSIUS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.CELSIUS)

    val weatherProvider: StateFlow<WeatherProviderType> = dataStore.data
        .map { prefs -> WeatherProviderPreferences.resolve(prefs[KEY_WEATHER_PROVIDER]) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            WeatherProviderPreferences.defaultProvider()
        )

    val availableWeatherProviders: List<WeatherProviderType> = WeatherProviderPreferences.availableProviders()

    val availableForecastDayOptions: StateFlow<List<Int>> = weatherProvider
        .map { it.supportedForecastDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), weatherProvider.value.supportedForecastDays)

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
        .map { prefs -> prefs[KEY_CLOCK_THEME] ?: CLOCK_THEME_LIGHT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CLOCK_THEME_LIGHT)

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

    val isHighPrecisionEnabled: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_HIGH_PRECISION] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val flipAnimationEnabled: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_FLIP_ANIMATION] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val weatherRefreshIntervalMinutes: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[KEY_WEATHER_REFRESH_INTERVAL] ?: DEFAULT_WEATHER_REFRESH_INTERVAL_MINUTES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_WEATHER_REFRESH_INTERVAL_MINUTES)

    val forecastDays: StateFlow<Int> = dataStore.data
        .map { prefs ->
            val provider = WeatherProviderPreferences.resolve(prefs[KEY_WEATHER_PROVIDER])
            val requestedDays = prefs[KEY_FORECAST_DAYS]
                ?: smartDefaultForecastDays(context.resources.configuration.screenWidthDp)
            normalizeForecastDaysForProvider(requestedDays, provider)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    private val _isExactAlarmPermissionGranted = MutableStateFlow(checkExactAlarmPermission())
    val isExactAlarmPermissionGranted = _isExactAlarmPermissionGranted.asStateFlow()

    private val _isBatteryOptimizationExempt = MutableStateFlow(checkBatteryOptimizationExempt())
    val isBatteryOptimizationExempt = _isBatteryOptimizationExempt.asStateFlow()

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

    fun setWeatherProvider(provider: WeatherProviderType) {
        if (!WeatherProviderPreferences.isConfigured(provider)) return

        viewModelScope.launch {
            val normalizedForecastDays = normalizeForecastDaysForProvider(
                requestedDays = dataStore.data.first()[KEY_FORECAST_DAYS]
                    ?: smartDefaultForecastDays(context.resources.configuration.screenWidthDp),
                provider = provider
            )
            dataStore.edit {
                it[KEY_WEATHER_PROVIDER] = provider.storageValue
                it[KEY_FORECAST_DAYS] = normalizedForecastDays
            }
            runCatching {
                refreshWeatherForProviderChange(
                    locationRepository = locationRepository,
                    weatherRepository = weatherRepository,
                    forecastDays = normalizedForecastDays
                )
            }
            triggerWidgetUpdate()
            com.clockweather.app.worker.WeatherUpdateScheduler.scheduleImmediateRefresh(context)
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
            // B3: immediately reschedule the periodic worker with the new interval
            com.clockweather.app.worker.WeatherUpdateScheduler.schedule(context, minutes)
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

    fun setHighPrecisionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_HIGH_PRECISION] = enabled }
            // B2: pass the new value directly — don't rely on DataStore read racing the write
            com.clockweather.app.receiver.ClockAlarmReceiver.scheduleNextTick(context, enabled)
            triggerWidgetUpdate()
        }
    }

    fun setFlipAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_FLIP_ANIMATION] = enabled }
            // Clear baseline state so widgets do a full rebuild with the new mode
            resetClockStateForActiveWidgets()
            triggerWidgetUpdate()
        }
    }

    fun setWeatherRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            val validMinutes = minutes.coerceIn(5, 1440)  // 5 min to 24 hours
            dataStore.edit { it[KEY_WEATHER_REFRESH_INTERVAL] = validMinutes }
        }
    }

    fun setForecastDays(days: Int) {
        viewModelScope.launch {
            val normalizedDays = normalizeForecastDaysForProvider(days, weatherProvider.value)
            dataStore.edit { it[KEY_FORECAST_DAYS] = normalizedDays }
            triggerWidgetUpdate()
        }
    }

    private fun resetClockStateForActiveWidgets() {
        val mgr = AppWidgetManager.getInstance(context)
        val providerClasses = listOf(
            com.clockweather.app.presentation.widget.compact.CompactWidgetProvider::class.java,
            com.clockweather.app.presentation.widget.extended.ExtendedWidgetProvider::class.java,
            com.clockweather.app.presentation.widget.forecast.ForecastWidgetProvider::class.java,
            com.clockweather.app.presentation.widget.large.LargeWidgetProvider::class.java
        )
        providerClasses.forEach { providerClass ->
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, providerClass))
            ids.forEach { id ->
                com.clockweather.app.presentation.widget.common.WidgetClockStateStore.clearWidget(context, id)
            }
        }
    }

    fun refreshPermissionStatus() {
        _isExactAlarmPermissionGranted.value = checkExactAlarmPermission()
        _isBatteryOptimizationExempt.value = checkBatteryOptimizationExempt()
    }

    private fun checkExactAlarmPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true // Pre-Android 12 doesn't have this restriction
    }

    /**
     * Returns true if this app is excluded from battery optimizations (i.e. set to
     * "Unrestricted" in Battery > App battery usage). When optimized, the OS may kill
     * the process while the screen is on, causing the widget clock to freeze until the
     * next AlarmManager wake-up or screen-off/on cycle.
     */
    private fun checkBatteryOptimizationExempt(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ── Private ────────────────────────────────────────────────────────────────

    /**
     * Kicks all active widgets to re-draw immediately after a pref change.
     */
    private fun triggerWidgetUpdate() {
        val app = context.applicationContext as? ClockWeatherApplication
        viewModelScope.launch {
            // Clear stored digit state so the refresh uses updateAppWidget()
            // (full replacement) — necessary because theme/size/style changed.
            app?.invalidateAllWidgetBaselines()
            app?.refreshAllWidgets(context, isClockTick = false)
        }
    }
}

internal suspend fun refreshWeatherForProviderChange(
    locationRepository: LocationRepository,
    weatherRepository: WeatherRepository,
    forecastDays: Int
) {
    val location = locationRepository.getSavedLocations().first().firstOrNull()
        ?: locationRepository.getCurrentLocation()
        ?: locationRepository.getFallbackLocation()
    weatherRepository.refreshWeatherData(location, forecastDays)
}
