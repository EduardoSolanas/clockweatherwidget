package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.R
import com.clockweather.app.presentation.common.UiState
import com.clockweather.app.presentation.detail.WeatherDetailViewModel
import com.clockweather.app.util.DateFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun WeatherDetailScreen(
    viewModel: WeatherDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val locationPermissionState = com.google.accompanist.permissions.rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )

    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            viewModel.refresh()
        }
    }

    // Auto-request if not granted and haven't shown yet
    SideEffect {
        if (!locationPermissionState.allPermissionsGranted && !locationPermissionState.shouldShowRationale) {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()

    val locationName = (uiState as? UiState.Success)?.data?.location?.name ?: stringResource(R.string.label_weather_fallback_title)

    // Lift selected day index here so TopAppBar can react to it
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val forecasts = (uiState as? UiState.Success)?.data?.dailyForecasts?.take(7) ?: emptyList()

    // Build title: "London" for today, "London · Sat, 8 Mar" for other days
    val topBarTitle = if (selectedDayIndex == 0 || forecasts.isEmpty()) {
        locationName
    } else {
        val date = forecasts.getOrNull(selectedDayIndex)?.date
        val dateStr = date?.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) ?: ""
        "$locationName  ·  $dateStr"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.label_loading_weather),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isPermissionError = !locationPermissionState.allPermissionsGranted
                        Text(
                            text = if (isPermissionError) 
                                stringResource(R.string.error_location_permission_required)
                                else state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (isPermissionError) {
                            Button(onClick = { locationPermissionState.launchMultiplePermissionRequest() }) {
                                Text(stringResource(R.string.action_grant_permission))
                            }
                        } else {
                            Button(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        WeatherDetailContent(
                            weatherData = state.data,
                            temperatureUnit = temperatureUnit,
                            selectedDayIndex = selectedDayIndex,
                            onDaySelected = { selectedDayIndex = it }
                        )
                    }
                }
            }
            // Show a progress bar during initial load or background refresh on non-Success states
            if (isRefreshing && uiState !is UiState.Success) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}

