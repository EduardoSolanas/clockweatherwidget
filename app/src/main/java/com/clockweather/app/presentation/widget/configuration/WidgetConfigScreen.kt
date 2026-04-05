package com.clockweather.app.presentation.widget.configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.R
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.presentation.common.UiState

@Composable
fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel,
    onConfigComplete: () -> Unit
) {
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.config_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // GPS Location Button
        OutlinedButton(
            onClick = { viewModel.useCurrentLocation() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.config_use_current_location))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // City Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            label = { Text(stringResource(R.string.config_search_city)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Search Results
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
        ) {
            when (val state = searchResults) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.data) { location ->
                            LocationItem(
                                location = location,
                                isSelected = location == selectedLocation,
                                onClick = { viewModel.selectLocation(location) }
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // Selected Location Display
        selectedLocation?.let { location ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.config_selected_format, location.name, location.country),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Temperature Unit
        Text(stringResource(R.string.config_temperature_unit), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TemperatureUnit.entries.forEach { unit ->
                FilterChip(
                    selected = unit == temperatureUnit,
                    onClick = { viewModel.setTemperatureUnit(unit) },
                    label = { Text(unit.symbol) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm
        Button(
            onClick = onConfigComplete,
            enabled = selectedLocation != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.config_add_widget))
        }
    }
}

@Composable
private fun LocationItem(
    location: Location,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(location.name) },
        supportingContent = { Text(location.country) },
        trailingContent = {
            if (isSelected) Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.cd_location_selected))
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
