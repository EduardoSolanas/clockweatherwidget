package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.R
import com.clockweather.app.presentation.common.UiState
import com.clockweather.app.presentation.detail.WeatherDetailViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun normalizeSelectedDayIndex(selectedDayIndex: Int, forecastCount: Int): Int {
    if (forecastCount <= 0) return 0
    return selectedDayIndex.takeIf { it in 0 until forecastCount } ?: 0
}

internal fun buildWeatherTopBarTitle(
    locationName: String,
    selectedDayIndex: Int,
    forecasts: List<com.clockweather.app.domain.model.DailyForecast>,
    locale: Locale = Locale.getDefault()
): String {
    val normalizedIndex = normalizeSelectedDayIndex(selectedDayIndex, forecasts.size)
    if (normalizedIndex == 0 || forecasts.isEmpty()) return locationName

    val date = forecasts.getOrNull(normalizedIndex)?.date ?: return locationName
    val dateStr = date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
    return "$locationName  ·  $dateStr"
}

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
    val forecastDays by viewModel.forecastDays.collectAsStateWithLifecycle()
    val needsBattery by viewModel.needsBatteryExemption.collectAsStateWithLifecycle()
    val needsAlarm by viewModel.needsExactAlarmPermission.collectAsStateWithLifecycle()
    val showSetupBanner = needsBattery || needsAlarm

    // Re-check permissions whenever the user returns from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    val locationName = (uiState as? UiState.Success)?.data?.location?.name ?: stringResource(R.string.label_weather_fallback_title)

    // Lift selected day index here so TopAppBar can react to it
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val forecasts = (uiState as? UiState.Success)?.data?.dailyForecasts?.take(forecastDays) ?: emptyList()
    selectedDayIndex = normalizeSelectedDayIndex(selectedDayIndex, forecasts.size)

    // Build title: "London" for today, "London · Sat, 8 Mar" for other days
    val topBarTitle = buildWeatherTopBarTitle(locationName, selectedDayIndex, forecasts)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Widget reliability setup banner ───────────────────────────
            // Shown until the user grants both battery exemption + exact alarm.
            // Dismisses automatically once both are granted (ON_RESUME check).
            if (showSetupBanner) {
                WidgetSetupBanner(
                    needsBattery = needsBattery,
                    needsAlarm = needsAlarm,
                    onSetupClick = onNavigateToSettings
                )
            }

            Box(modifier = Modifier.weight(1f)) {
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
                            onDaySelected = { selectedDayIndex = it },
                            forecastDays = forecastDays
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
            } // end inner Box
        } // end Column
    }
}

@Composable
private fun WidgetSetupBanner(
    needsBattery: Boolean,
    needsAlarm: Boolean,
    onSetupClick: () -> Unit
) {
    val bulletBattery = stringResource(R.string.setup_banner_bullet_battery)
    val bulletAlarm   = stringResource(R.string.setup_banner_bullet_alarm)
    val missing = buildList {
        if (needsBattery) add(bulletBattery)
        if (needsAlarm)   add(bulletAlarm)
    }.joinToString(" · ")

    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setup_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = missing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSetupClick) {
                Text(
                    text = stringResource(R.string.setup_banner_action),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

