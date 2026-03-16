package com.clockweather.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.R
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.ClockTileSize
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val temperatureUnit  by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val use24hClock      by viewModel.use24hClock.collectAsStateWithLifecycle()
    val showDateInWidget by viewModel.showDateInWidget.collectAsStateWithLifecycle()
    val showTodayCompact by viewModel.showTodayCompact.collectAsStateWithLifecycle()
    val showTodayExtended by viewModel.showTodayExtended.collectAsStateWithLifecycle()
    val dateFontSizeSp   by viewModel.dateFontSizeSp.collectAsStateWithLifecycle()
    val clockTheme       by viewModel.clockTheme.collectAsStateWithLifecycle()
    val clockTileSize    by viewModel.clockTileSize.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ══════════════════════════════════════════════════════════
            // 🕐  CLOCK
            // ══════════════════════════════════════════════════════════
            SettingsSectionHeader(stringResource(R.string.settings_section_clock))

            // Tile style
            SettingsLabel(
                label = stringResource(R.string.settings_clock_tile_style_label),
                description = stringResource(R.string.settings_clock_tile_style_desc)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = clockTheme == SettingsViewModel.CLOCK_THEME_DARK,
                    onClick  = { viewModel.setClockTheme(SettingsViewModel.CLOCK_THEME_DARK) },
                    label    = { Text(stringResource(R.string.settings_clock_tile_style_dark)) }
                )
                FilterChip(
                    selected = clockTheme == SettingsViewModel.CLOCK_THEME_LIGHT,
                    onClick  = { viewModel.setClockTheme(SettingsViewModel.CLOCK_THEME_LIGHT) },
                    label    = { Text(stringResource(R.string.settings_clock_tile_style_light)) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Tile size
            SettingsLabel(
                label = stringResource(R.string.settings_clock_tile_size_label),
                description = stringResource(R.string.settings_clock_tile_size_desc)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClockTileSize.entries.forEach { size ->
                    FilterChip(
                        selected = size == clockTileSize,
                        onClick  = { viewModel.setClockTileSize(size) },
                        label    = { Text(stringResource(size.labelResId)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsToggleRow(
                label       = stringResource(R.string.settings_24h_clock_label),
                description = stringResource(R.string.settings_24h_clock_desc),
                checked     = use24hClock,
                onCheckedChange = { viewModel.set24hClock(it) }
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════════
            // 📅  DATE
            // ══════════════════════════════════════════════════════════
            SettingsSectionHeader(stringResource(R.string.settings_section_date))

            SettingsToggleRow(
                label       = stringResource(R.string.settings_show_date_label),
                description = stringResource(R.string.settings_show_date_desc),
                checked     = showDateInWidget,
                onCheckedChange = { viewModel.setShowDateInWidget(it) }
            )

            if (showDateInWidget) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_date_font_size_label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${dateFontSizeSp.roundToInt()} sp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value         = dateFontSizeSp,
                        onValueChange = { viewModel.setDateFontSize(it) },
                        valueRange    = 10f..22f,
                        steps         = 11,
                        modifier      = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════════
            // 🌤  WEATHER
            // ══════════════════════════════════════════════════════════
            SettingsSectionHeader(stringResource(R.string.settings_section_weather))

            SettingsLabel(
                label       = stringResource(R.string.settings_temperature_unit),
                description = stringResource(R.string.settings_temperature_unit)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TemperatureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = unit == temperatureUnit,
                        onClick  = { viewModel.setTemperatureUnit(unit) },
                        label = {
                            Text(
                                when (unit) {
                                    TemperatureUnit.CELSIUS    -> stringResource(R.string.settings_temp_celsius)
                                    TemperatureUnit.FAHRENHEIT -> stringResource(R.string.settings_temp_fahrenheit)
                                }
                            )
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Language
            val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
            SettingsLabel(
                label       = stringResource(R.string.settings_language),
                description = stringResource(R.string.settings_language)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "system" to stringResource(R.string.settings_language_system),
                    "en" to stringResource(R.string.settings_language_en),
                    "es" to stringResource(R.string.settings_language_es)
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = selectedLanguage == code,
                        onClick  = { viewModel.setLanguage(code) },
                        label    = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════════
            // 📱  WIDGET
            // ══════════════════════════════════════════════════════════
            SettingsSectionHeader(stringResource(R.string.settings_section_widget))

            SettingsToggleRow(
                label       = stringResource(R.string.settings_show_weather_compact_label),
                description = stringResource(R.string.settings_show_weather_compact_desc),
                checked     = showTodayCompact,
                onCheckedChange = { viewModel.setShowTodayCompact(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsToggleRow(
                label       = stringResource(R.string.settings_show_today_forecast_label),
                description = stringResource(R.string.settings_show_today_forecast_desc),
                checked     = showTodayExtended,
                onCheckedChange = { viewModel.setShowTodayExtended(it) }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsLabel(label: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label,       style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            modifier        = Modifier.padding(start = 16.dp)
        )
    }
}
