package com.clockweather.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.domain.model.TemperatureUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val use24hClock by viewModel.use24hClock.collectAsStateWithLifecycle()
    val showDateInWidget by viewModel.showDateInWidget.collectAsStateWithLifecycle()
    val showTodayCompact by viewModel.showTodayCompact.collectAsStateWithLifecycle()
    val showTodayExtended by viewModel.showTodayExtended.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Temperature Unit ──────────────────────────────────────────────
            SettingsSectionHeader("Temperature")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TemperatureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = unit == temperatureUnit,
                        onClick = { viewModel.setTemperatureUnit(unit) },
                        label = {
                            Text(
                                text = when (unit) {
                                    TemperatureUnit.CELSIUS -> "°C — Celsius"
                                    TemperatureUnit.FAHRENHEIT -> "°F — Fahrenheit"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Widget Display ────────────────────────────────────────────────
            SettingsSectionHeader("Widget")

            SettingsToggleRow(
                label = "Show date",
                description = "Display the current date below the clock",
                checked = showDateInWidget,
                onCheckedChange = { viewModel.setShowDateInWidget(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsToggleRow(
                label = "24-hour clock",
                description = "Use 24h format instead of AM/PM",
                checked = use24hClock,
                onCheckedChange = { viewModel.set24hClock(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsToggleRow(
                label = "Show today — compact widget",
                description = "Display current weather below the clock (on by default)",
                checked = showTodayCompact,
                onCheckedChange = { viewModel.setShowTodayCompact(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsToggleRow(
                label = "Show today — forecast widget",
                description = "Display today's weather above the 7-day rows (off by default)",
                checked = showTodayExtended,
                onCheckedChange = { viewModel.setShowTodayExtended(it) }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
