package com.clockweather.app.presentation.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.R
import com.clockweather.app.domain.model.SpeedUnit
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.util.BackgroundLocationAccess

@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val tempUnit by settingsViewModel.temperatureUnit.collectAsStateWithLifecycle()
    val speedUnit by settingsViewModel.speedUnit.collectAsStateWithLifecycle()
    val use24hClock by settingsViewModel.use24hClock.collectAsStateWithLifecycle()

    var isForegroundGranted by remember { mutableStateOf(BackgroundLocationAccess.isForegroundGranted(context)) }
    var isBackgroundGranted by remember { mutableStateOf(BackgroundLocationAccess.isBackgroundGranted(context)) }
    var isBatteryExempt by remember { mutableStateOf(checkBatteryExempt(context)) }

    fun refreshState() {
        isForegroundGranted = BackgroundLocationAccess.isForegroundGranted(context)
        isBackgroundGranted = BackgroundLocationAccess.isBackgroundGranted(context)
        isBatteryExempt = checkBatteryExempt(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshState()
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            OnboardingBottomBar(
                currentStep = currentStep,
                totalSteps = 5,
                onBack = { if (currentStep > 0) currentStep-- },
                onNext = {
                    if (currentStep < 4) {
                        currentStep++
                    } else {
                        onComplete()
                    }
                },
                onSkip = {
                    if (currentStep < 4) currentStep++ else onComplete()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "onboarding_step_transition"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (step) {
                        0 -> WelcomeStep(onGetStarted = { currentStep = 1 })
                        1 -> LocationStep(
                            context = context,
                            isForegroundGranted = isForegroundGranted,
                            isBackgroundGranted = isBackgroundGranted,
                            onRefresh = { refreshState() }
                        )
                        2 -> BatteryStep(
                            context = context,
                            isBatteryExempt = isBatteryExempt,
                            onRefresh = { refreshState() }
                        )
                        3 -> PreferencesStep(
                            tempUnit = tempUnit,
                            speedUnit = speedUnit,
                            use24hClock = use24hClock,
                            onSetTempUnit = settingsViewModel::setTemperatureUnit,
                            onSetSpeedUnit = settingsViewModel::setSpeedUnit,
                            onSet24hClock = settingsViewModel::set24hClock
                        )
                        4 -> AllSetStep(onFinish = onComplete)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    StepIconBox(icon = Icons.Default.WbSunny, containerColor = MaterialTheme.colorScheme.primaryContainer)

    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.onboarding_welcome_desc),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 24.sp
    )

    Spacer(Modifier.height(16.dp))

    FeatureHighlightCard(
        icon = Icons.Default.Schedule,
        title = stringResource(R.string.onboarding_feature_flip_clock_title),
        desc = stringResource(R.string.onboarding_feature_flip_clock_desc)
    )

    FeatureHighlightCard(
        icon = Icons.Default.WbSunny,
        title = stringResource(R.string.onboarding_feature_weather_title),
        desc = stringResource(R.string.onboarding_feature_weather_desc)
    )

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = onGetStarted,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(stringResource(R.string.onboarding_get_started), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LocationStep(
    context: Context,
    isForegroundGranted: Boolean,
    isBackgroundGranted: Boolean,
    onRefresh: () -> Unit
) {
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefresh()
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefresh()
    }

    StepIconBox(icon = Icons.Default.LocationOn, containerColor = MaterialTheme.colorScheme.tertiaryContainer)

    Text(
        text = stringResource(R.string.onboarding_location_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.onboarding_location_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    // Foreground Permission Card
    PermissionActionCard(
        title = stringResource(R.string.onboarding_location_precise_title),
        description = stringResource(R.string.onboarding_location_precise_desc),
        isGranted = isForegroundGranted,
        actionLabel = stringResource(R.string.onboarding_grant_location),
        onAction = {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    )

    // Background Permission Card
    PermissionActionCard(
        title = stringResource(R.string.onboarding_location_background_title),
        description = stringResource(R.string.onboarding_location_background_desc),
        isGranted = isBackgroundGranted,
        actionLabel = stringResource(R.string.onboarding_grant_background_location),
        onAction = {
            if (!isForegroundGranted) {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    )
}

@Composable
private fun BatteryStep(
    context: Context,
    isBatteryExempt: Boolean,
    onRefresh: () -> Unit
) {
    StepIconBox(icon = Icons.Default.BatteryChargingFull, containerColor = MaterialTheme.colorScheme.secondaryContainer)

    Text(
        text = stringResource(R.string.onboarding_battery_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.onboarding_battery_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    PermissionActionCard(
        title = stringResource(R.string.onboarding_battery_unrestricted_title),
        description = stringResource(R.string.onboarding_battery_unrestricted_desc),
        isGranted = isBatteryExempt,
        actionLabel = stringResource(R.string.onboarding_battery_action),
        onAction = {
            requestBatteryOptimizationExemption(context)
            onRefresh()
        }
    )
}

@Composable
private fun PreferencesStep(
    tempUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    use24hClock: Boolean,
    onSetTempUnit: (TemperatureUnit) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    onSet24hClock: (Boolean) -> Unit
) {
    StepIconBox(icon = Icons.Default.Settings, containerColor = MaterialTheme.colorScheme.primaryContainer)

    Text(
        text = stringResource(R.string.onboarding_preferences_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.onboarding_preferences_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    // Temperature Unit
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.onboarding_pref_temp_unit), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TemperatureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = unit == tempUnit,
                        onClick = { onSetTempUnit(unit) },
                        label = {
                            Text(
                                when (unit) {
                                    TemperatureUnit.CELSIUS -> stringResource(R.string.settings_temp_celsius)
                                    TemperatureUnit.FAHRENHEIT -> stringResource(R.string.settings_temp_fahrenheit)
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    // Clock Format
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.onboarding_pref_clock_format), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = use24hClock,
                    onClick = { onSet24hClock(true) },
                    label = { Text(stringResource(R.string.onboarding_pref_clock_24h)) }
                )
                FilterChip(
                    selected = !use24hClock,
                    onClick = { onSet24hClock(false) },
                    label = { Text(stringResource(R.string.onboarding_pref_clock_12h)) }
                )
            }
        }
    }

    // Speed Unit
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.onboarding_pref_wind_speed), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = unit == speedUnit,
                        onClick = { onSetSpeedUnit(unit) },
                        label = { Text(unit.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllSetStep(onFinish: () -> Unit) {
    Spacer(Modifier.height(30.dp))
    StepIconBox(icon = Icons.Default.CheckCircle, containerColor = MaterialTheme.colorScheme.primaryContainer)

    Text(
        text = stringResource(R.string.onboarding_all_set_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.onboarding_all_set_desc),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 24.sp
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onFinish,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(stringResource(R.string.onboarding_finish_btn), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepIconBox(icon: ImageVector, containerColor: Color) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun FeatureHighlightCard(icon: ImageVector, title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionActionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isGranted) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (isGranted) {
                    Text(
                        text = stringResource(R.string.onboarding_granted),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!isGranted) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            // Step Dots
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    val isSelected = index == currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0 && currentStep < totalSteps - 1) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else if (currentStep < totalSteps - 1) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (currentStep < totalSteps - 1) {
                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

private fun checkBatteryExempt(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestBatteryOptimizationExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }
}
