package com.clockweather.app.presentation.detail.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.clockweather.app.R
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.SpeedUnit
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.currentHourWeather
import com.clockweather.app.domain.model.currentHourTemperature
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import com.clockweather.app.util.WindSpeedFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

// ─── Today resolution helper (kept internal for tests) ───────────────────────

internal fun resolveForecastIsToday(forecastDate: LocalDate, today: LocalDate): Boolean =
    forecastDate == today

internal data class ForecastDayTextColors(
    val primary: Color,
    val secondary: Color
)

internal fun forecastDayTextColors(isSelected: Boolean): ForecastDayTextColors =
    ForecastDayTextColors(
        primary = Color.White,
        secondary = Color.White.copy(alpha = if (isSelected) 0.72f else 0.76f)
    )

internal data class ForecastDayCompactTextSizes(
    val highSp: Float,
    val lowSp: Float,
    val precipitationSp: Float,
    val windSp: Float,
    val daySp: Float,
    val dateSp: Float
)

private fun ForecastDayCompactTextSizes.scaled(scale: Float): ForecastDayCompactTextSizes =
    ForecastDayCompactTextSizes(
        highSp = highSp * scale,
        lowSp = lowSp * scale,
        precipitationSp = precipitationSp * scale,
        windSp = windSp * scale,
        daySp = daySp * scale,
        dateSp = dateSp * scale
    )

internal fun forecastDayDensityTextScale(densityDpi: Int): Float = when {
    densityDpi <= 280 -> 0.88f
    densityDpi <= 360 -> 0.94f
    else -> 1f
}

internal fun forecastDayCompactTextSizes(
    columnWidthDp: Float,
    densityDpi: Int
): ForecastDayCompactTextSizes {
    val baseSizes = when {
        columnWidthDp <= 46f -> ForecastDayCompactTextSizes(
            highSp = 14f,
            lowSp = 12f,
            precipitationSp = 10.5f,
            windSp = 9.5f,
            daySp = 12f,
            dateSp = 11f
        )
        columnWidthDp <= 50f -> ForecastDayCompactTextSizes(
            highSp = 15f,
            lowSp = 12.5f,
            precipitationSp = 11f,
            windSp = 10f,
            daySp = 12.5f,
            dateSp = 11.5f
        )
        else -> ForecastDayCompactTextSizes(
            highSp = 16f,
            lowSp = 13f,
            precipitationSp = 11.5f,
            windSp = 10.5f,
            daySp = 13f,
            dateSp = 12f
        )
    }
    return baseSizes.scaled(forecastDayDensityTextScale(densityDpi))
}

internal fun currentTemperatureDisplay(
    weatherData: WeatherData,
    temperatureUnit: TemperatureUnit,
    referenceDateTime: LocalDateTime = LocalDateTime.now()
): String = TemperatureFormatter.formatWithUnit(
    weatherData.currentHourTemperature(referenceDateTime),
    temperatureUnit
)

internal fun currentWeatherForDisplay(
    weatherData: WeatherData,
    referenceDateTime: LocalDateTime = LocalDateTime.now()
) = weatherData.currentHourWeather(referenceDateTime)

// ─── Debug: all conditions in cycle order ─────────────────────────────────────
private val DEBUG_CONDITIONS = WeatherCondition.entries.toList()

// ─── Main content ─────────────────────────────────────────────────────────────

@Composable
fun WeatherDetailContent(
    weatherData: WeatherData,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    selectedDayIndex: Int = 0,
    onDaySelected: (Int) -> Unit = {},
    forecastDays: Int = 7
) {
    val forecasts = selectWeatherDetailForecasts(weatherData.dailyForecasts, forecastDays)
    val selectedForecast = forecasts.getOrNull(selectedDayIndex) ?: forecasts.firstOrNull()
    val displayWeatherData = weatherData.copy(dailyForecasts = forecasts)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Hero card ──────────────────────────────────────────────────────
        HeroWeatherCard(
            weatherData = displayWeatherData,
            temperatureUnit = temperatureUnit,
            selectedDayIndex = selectedDayIndex
        )

        // ── Hourly Forecast Graph ──────────────────────────────────────────
        HourlyWeatherGraph(
            hourlyForecasts = weatherData.hourlyForecasts,
            temperatureUnit = temperatureUnit,
            speedUnit = speedUnit,
            selectedDate = selectedForecast?.date
        )

        // ── 7-Day Forecast ─────────────────────────────────────────────────
        SevenDayForecastCard(
            forecasts = forecasts,
            temperatureUnit = temperatureUnit,
            speedUnit = speedUnit,
            selectedDayIndex = selectedDayIndex,
            onDaySelected = onDaySelected
        )

        // ── Detail metrics grid ─────────────────────────────────────────────
        MetricsGrid(weatherData = displayWeatherData, temperatureUnit = temperatureUnit, speedUnit = speedUnit, selectedDayIndex = selectedDayIndex)

        // ── Sunrise / Sunset ────────────────────────────────────────────────
        val shownForecast = selectedForecast
        shownForecast?.let { SunCard(forecast = it) }

        // ── Air Quality (today only) ────────────────────────────────────────
        if (selectedDayIndex == 0) {
            weatherData.airQuality?.let { AirQualityCard(it) }
        }

        // ── Last updated ────────────────────────────────────────────────────
        val lastUpdated = weatherData.currentWeather.lastUpdated
        val minutes = DateFormatter.minutesAgo(lastUpdated)
        val timeString = when {
            minutes < 1 -> stringResource(R.string.label_just_now)
            minutes < 60 -> pluralStringResource(R.plurals.label_minutes_ago, minutes, minutes)
            else -> {
                val hours = minutes / 60
                pluralStringResource(R.plurals.label_hours_ago, hours, hours)
            }
        }
        Text(
            text = stringResource(R.string.label_updated_format, timeString),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroWeatherCard(
    weatherData: WeatherData,
    temperatureUnit: TemperatureUnit,
    selectedDayIndex: Int
) {
    val current = if (selectedDayIndex == 0) currentWeatherForDisplay(weatherData) else weatherData.currentWeather
    val forecasts = weatherData.dailyForecasts
    val selectedForecast = forecasts.getOrNull(selectedDayIndex)

    // ── DEBUG: tap state (must be at top to be usable in calculations)
    var debugIndex by remember { mutableIntStateOf(-1) }

    val weatherCondition = when {
        debugIndex >= 0 -> DEBUG_CONDITIONS[debugIndex]
        selectedDayIndex == 0 -> current.weatherCondition
        else -> selectedForecast?.weatherCondition ?: current.weatherCondition
    }

    // ── Dynamic Background with Time-of-Day Base & Weather Overrides ────────
    val isDay = current.isDay
    val backgroundBrush = remember(weatherCondition, isDay) {
        val colors = when {
            // Priority 1: Heavy Weather Overrides
            weatherCondition == WeatherCondition.THUNDERSTORM || 
            weatherCondition == WeatherCondition.THUNDERSTORM_SLIGHT_HAIL ||
            weatherCondition == WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> listOf(Color(0xFF37474F), Color(0xFF102027)) // Storm Dark
            
            weatherCondition == WeatherCondition.OVERCAST || 
            weatherCondition == WeatherCondition.FOG ||
            weatherCondition == WeatherCondition.DEPOSITING_RIME_FOG -> listOf(Color(0xFF90A4AE), Color(0xFF455A64)) // Gray Overcast/Fog
            
            weatherCondition.name.contains("RAIN") || 
            weatherCondition.name.contains("DRIZZLE") -> listOf(Color(0xFF455A64), Color(0xFF263238)) // Rainy Shadow
            
            weatherCondition.name.contains("SNOW") -> listOf(Color(0xFFB3E5FC), Color(0xFF81D4FA)) // Snowy Light Blue
            
            // Priority 2: Time of Day Fallback (for Clear/Partly Cloudy)
            isDay -> listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // Bright Blue Day
            else -> listOf(Color(0xFF1A237E), Color(0xFF000051)) // Deep Midnight Night
        }
        Brush.verticalGradient(colors)
    }

    val displayCondition = if (debugIndex < 0) weatherCondition else DEBUG_CONDITIONS[debugIndex]
    val conditionLabelId = displayCondition.labelResId
    val condition = stringResource(conditionLabelId)
    val tempDisplay = if (selectedDayIndex == 0)
        TemperatureFormatter.formatWithUnit(current.temperature, temperatureUnit)
    else
        "${TemperatureFormatter.format(selectedForecast?.temperatureMax ?: 0.0, temperatureUnit)}°"

    val feelsLike = if (selectedDayIndex == 0) {
        stringResource(
            R.string.label_feels_like_format,
            TemperatureFormatter.formatWithUnit(current.feelsLikeTemperature, temperatureUnit)
        )
    } else {
        stringResource(
            R.string.label_feels_like_range_format,
            "${TemperatureFormatter.format(selectedForecast?.feelsLikeMax ?: 0.0, temperatureUnit)}°",
            "${TemperatureFormatter.format(selectedForecast?.feelsLikeMin ?: 0.0, temperatureUnit)}°"
        )
    }



    val humidity = if (selectedDayIndex == 0) current.humidity else selectedForecast?.averageHumidity ?: 0
    val windSpeed = if (selectedDayIndex == 0) current.windSpeed.toInt() else selectedForecast?.windSpeedMax?.toInt() ?: 0
    val windDir = if (selectedDayIndex == 0) stringResource(current.windDirection.labelResId) else stringResource(selectedForecast?.windDirectionDominant?.labelResId ?: R.string.wind_n)
    val uv = if (selectedDayIndex == 0) current.uvIndex.toInt() else selectedForecast?.uvIndexMax?.toInt() ?: 0
    val precipitationDisplay = stringResource(R.string.unit_percent, if (selectedDayIndex == 0) current.precipitationProbability else selectedForecast?.precipitationProbability ?: 0)

    val debugLabel = if (debugIndex >= 0) "🐛 ${displayCondition.name}" else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(backgroundBrush)
            .clickable { debugIndex = (debugIndex + 1) % DEBUG_CONDITIONS.size }
    ) {
        // Animated weather sprite.
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(start = 24.dp, bottom = 20.dp)
                .align(Alignment.BottomStart)
        ) {
            WeatherAnimatedIcon(
                condition = displayCondition,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Content on top
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = (if (debugIndex >= 0) stringResource(displayCondition.labelResId) else condition).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        lineHeight = 18.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tempDisplay,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.ExtraLight,
                            color = Color.White,
                            lineHeight = 72.sp
                        )

                        Spacer(Modifier.width(8.dp))

                        Column {
                            val highStr = if (selectedDayIndex == 0)
                                "${TemperatureFormatter.format(selectedForecast?.temperatureMax ?: 0.0, temperatureUnit)}°"
                            else
                                "${TemperatureFormatter.format(selectedForecast?.temperatureMax ?: 0.0, temperatureUnit)}°"

                            val lowStr = if (selectedDayIndex == 0)
                                "${TemperatureFormatter.format(selectedForecast?.temperatureMin ?: 0.0, temperatureUnit)}°"
                            else
                                "${TemperatureFormatter.format(selectedForecast?.temperatureMin ?: 0.0, temperatureUnit)}°"

                            Text(
                                text = stringResource(R.string.label_high_format, highStr),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.label_low_format, lowStr),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Text(
                        text = feelsLike,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Stats Glass Card - Moved lower to avoid overlap
                Column(
                    modifier = Modifier
                        .padding(top = 40.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroStat("💧", humidity.toString() + "%")
                    HeroStat("🌧",  precipitationDisplay)
                    HeroStat("💨", "${windSpeed} km/h")
                    HeroStat("☀️", "UV $uv")
                }
            }
            
            if (debugLabel != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.Start).padding(top = 16.dp)
                ) {
                    Text(
                        text = debugLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
    }
}

@Composable
private fun HeroStat(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

// ─── 7-Day / 14-Day forecast card ─────────────────────────────────────────────

@Composable
private fun SevenDayForecastCard(
    forecasts: List<DailyForecast>,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    val isScrollable = forecasts.size > 7

    val rawTitleText = when {
        forecasts.size >= 14 -> stringResource(R.string.label_14day_forecast)
        forecasts.size >= 7  -> stringResource(R.string.label_7day_forecast)
        else                 -> stringResource(R.string.label_nday_forecast, forecasts.size)
    }
    val titleText = remember(rawTitleText) {
        rawTitleText
            .lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val canScrollBackward by remember { derivedStateOf { scrollState.value > 0 } }
    val canScrollForward  by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

    // Use BoxWithConstraints so the scrollable row uses exactly the same per-column
    // width as the 7-day grid — preventing any size/spacing jump when toggling modes.
    @Suppress("UnusedBoxWithConstraintsScopeModifier")
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidthDp = maxWidth.value
        val columnWidthDp    = forecastColumnWidth(availableWidthDp)
        val columnWidth      = columnWidthDp.dp
        val density          = LocalDensity.current
        val scrollStepPx     = remember(columnWidthDp, density) {
            with(density) { forecastScrollStep(columnWidthDp).dp.roundToPx() }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header row ─────────────────────────────────────────────────
            if (isScrollable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    ForecastScrollButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        enabled = canScrollBackward,
                        onClick = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo((scrollState.value - scrollStepPx).coerceAtLeast(0))
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    ForecastScrollButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        enabled = canScrollForward,
                        onClick = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo((scrollState.value + scrollStepPx).coerceAtMost(scrollState.maxValue))
                            }
                        }
                    )
                }
            } else {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // ── Forecast columns ───────────────────────────────────────────
            // Both modes use the same spacing and isCompact so columns look
            // identical regardless of how many days are shown.
            Row(
                modifier = if (isScrollable)
                    Modifier.fillMaxWidth().horizontalScroll(scrollState)
                else
                    Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                forecasts.forEachIndexed { index, forecast ->
                    ForecastDayColumn(
                        forecast = forecast,
                        temperatureUnit = temperatureUnit,
                        speedUnit = speedUnit,
                        isToday = resolveForecastIsToday(forecast.date, LocalDate.now()),
                        isSelected = index == selectedDayIndex,
                        onClick = { onDaySelected(index) },
                        // scrollable → fixed width matching 7-day proportions
                        // non-scrollable → weight fills width equally (same visual result)
                        modifier = if (isScrollable)
                            Modifier.width(columnWidth)
                        else
                            Modifier.weight(1f),
                        isCompact = true   // always compact — consistent height + font sizes
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastScrollButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.95f else 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.size(44.dp)
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun ForecastDayColumn(
    forecast: DailyForecast,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val capsuleColor = if (isSelected) Color(0xFF141723) else Color(0xFF10131D)
    val outlineColor = if (isSelected) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val textColors = forecastDayTextColors(isSelected)
    val primaryTextColor = textColors.primary
    val secondaryTextColor = textColors.secondary
    val precipitationColor = Color(0xFF64B5F6)
    val dateLabel = remember(forecast.date) {
        forecast.date.format(DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault()))
    }
    val highLabel = TemperatureFormatter.format(forecast.temperatureMax, temperatureUnit) + "°"
    val lowLabel = TemperatureFormatter.format(forecast.temperatureMin, temperatureUnit) + "°"
    val windLabel = WindSpeedFormatter.formatWithUnit(forecast.windSpeedMax, speedUnit)
    val cornerRadius = 12.dp

    Surface(
        modifier = modifier
            .height(if (isCompact) 220.dp else 260.dp)
            .clickable(onClick = onClick)
            .border(2.dp, outlineColor, RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        color = capsuleColor,
        tonalElevation = if (isSelected) 6.dp else 0.dp,
        shadowElevation = if (isSelected) 10.dp else 0.dp
    ) {
        @Suppress("UnusedBoxWithConstraintsScopeModifier")
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactTextSizes = forecastDayCompactTextSizes(
                columnWidthDp = maxWidth.value,
                densityDpi = LocalConfiguration.current.densityDpi
            )
            val highTextStyle = if (isCompact) {
                MaterialTheme.typography.bodyMedium.copy(fontSize = compactTextSizes.highSp.sp)
            } else {
                MaterialTheme.typography.titleLarge
            }
            val lowTextStyle = if (isCompact) {
                MaterialTheme.typography.bodySmall.copy(fontSize = compactTextSizes.lowSp.sp)
            } else {
                MaterialTheme.typography.titleMedium
            }
            val precipitationTextStyle = if (isCompact) {
                MaterialTheme.typography.labelSmall.copy(fontSize = compactTextSizes.precipitationSp.sp)
            } else {
                MaterialTheme.typography.titleMedium
            }
            val windTextStyle = if (isCompact) {
                MaterialTheme.typography.labelSmall.copy(fontSize = compactTextSizes.windSp.sp)
            } else {
                MaterialTheme.typography.titleMedium
            }
            val dayTextStyle = if (isCompact) {
                MaterialTheme.typography.labelMedium.copy(fontSize = compactTextSizes.daySp.sp)
            } else {
                MaterialTheme.typography.titleMedium
            }
            val dateTextStyle = if (isCompact) {
                MaterialTheme.typography.labelSmall.copy(fontSize = compactTextSizes.dateSp.sp)
            } else {
                MaterialTheme.typography.titleMedium
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isCompact) 3.dp else 10.dp, vertical = if (isCompact) 8.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = highLabel,
                style = highTextStyle,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
                maxLines = 1
            )
            Text(
                text = lowLabel,
                style = lowTextStyle,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor,
                maxLines = 1
            )

            Spacer(Modifier.height(if (isCompact) 6.dp else 10.dp))

            Box(
                modifier = Modifier.size(if (isCompact) 28.dp else 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(forecast.weatherCondition.iconResId),
                    contentDescription = stringResource(forecast.weatherCondition.labelResId),
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(if (isCompact) 4.dp else 8.dp))

            Icon(
                painter = painterResource(R.drawable.ic_rain_drops),
                contentDescription = null,
                tint = precipitationColor,
                modifier = Modifier.size(if (isCompact) 14.dp else 18.dp)
            )
            Text(
                text = stringResource(R.string.unit_percent, forecast.precipitationProbability),
                style = precipitationTextStyle,
                color = precipitationColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Spacer(Modifier.height(if (isCompact) 4.dp else 8.dp))

            Icon(
                painter = painterResource(R.drawable.ic_widget_weather_wind_arrow),
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier
                    .size(if (isCompact) 20.dp else 24.dp)
                    .rotate(forecast.windDirectionDegrees.toFloat())
            )
            Text(
                text = windLabel,
                style = windTextStyle,
                color = secondaryTextColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = if (isToday) stringResource(R.string.label_today) else DateFormatter.formatDayName(forecast.date),
                style = dayTextStyle,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = primaryTextColor,
                maxLines = 1
            )
            Text(
                text = dateLabel,
                style = dateTextStyle,
                color = secondaryTextColor,
                maxLines = 1
            )
            }
        }
    }
}

@Composable
private fun ForecastDayRow(
    forecast: DailyForecast,
    temperatureUnit: TemperatureUnit,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        // Main row: day | precip% | condition | H° | L°
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isToday) stringResource(R.string.label_today) else DateFormatter.formatDayName(forecast.date),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(52.dp)
            )
            Text(
                text = if (forecast.precipitationProbability > 0) stringResource(R.string.unit_percent, forecast.precipitationProbability) else "",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64B5F6),
                modifier = Modifier.width(34.dp)
            )
            Text(
                text = stringResource(forecast.weatherCondition.labelResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit, forecast.temperatureMax),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.End
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit, forecast.temperatureMin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End
            )
        }
        // Always-visible detail row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SmallMetric("💧", stringResource(R.string.unit_mm, forecast.precipitationSum))
            SmallMetric("💨", WindSpeedFormatter.formatWithUnit(forecast.windSpeedMax, SpeedUnit.KMH))
            SmallMetric("☀️", "UV ${forecast.uvIndexMax.toInt()}")
            SmallMetric("🌅", DateFormatter.formatTime(forecast.sunrise, true))
            SmallMetric("🌇", DateFormatter.formatTime(forecast.sunset, true))
        }
    }
}

@Composable
private fun SmallMetric(icon: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(icon, fontSize = 10.sp)
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Metrics grid ─────────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(weatherData: WeatherData, temperatureUnit: TemperatureUnit, speedUnit: SpeedUnit, selectedDayIndex: Int) {
    val c = if (selectedDayIndex == 0) currentWeatherForDisplay(weatherData) else weatherData.currentWeather
    val f = weatherData.dailyForecasts.getOrNull(selectedDayIndex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.label_conditions),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        if (selectedDayIndex == 0) {
            // Current live data
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_precipitation), stringResource(R.string.unit_mm, c.precipitation), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_pressure), stringResource(R.string.unit_hpa, c.pressure), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_visibility), stringResource(R.string.unit_km, c.visibility / 1000.0), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_dew_point), TemperatureFormatter.formatWithUnit(c.dewPoint, temperatureUnit), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_cloud_cover), stringResource(R.string.unit_percent, c.cloudCover), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_wind_gusts), WindSpeedFormatter.formatWithUnit(c.windGusts, speedUnit), Modifier.weight(1f))
            }
        } else if (f != null) {
            // Daily forecast data
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_rain_total), stringResource(R.string.unit_mm, f.precipitationSum), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_pressure), stringResource(R.string.unit_hpa, f.averagePressure), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_humidity), stringResource(R.string.unit_percent, f.averageHumidity), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_max_wind), WindSpeedFormatter.formatWithUnit(f.windSpeedMax, speedUnit) + " " + stringResource(f.windDirectionDominant.labelResId), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_rain_chance), stringResource(R.string.unit_percent, f.precipitationProbability), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_uv_max), f.uvIndexMax.toInt().toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Sunrise / Sunset card ────────────────────────────────────────────────────

@Composable
private fun SunCard(forecast: DailyForecast) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌅", fontSize = 28.sp)
                Text(stringResource(R.string.label_sun_sunrise), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormatter.formatTime(forecast.sunrise, true), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            VerticalDivider(modifier = Modifier.height(60.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌇", fontSize = 28.sp)
                Text(stringResource(R.string.label_sun_sunset), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormatter.formatTime(forecast.sunset, true), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            VerticalDivider(modifier = Modifier.height(60.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⏱", fontSize = 28.sp)
                Text(stringResource(R.string.label_sun_daylight), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormatter.formatDuration(forecast.daylightDurationSeconds), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Air Quality card ─────────────────────────────────────────────────────────

@Composable
private fun AirQualityCard(aq: AirQuality) {
    val indexColor = when (aq.usEpaIndex) {
        1 -> Color(0xFF4CAF50)   // Good — green
        2 -> Color(0xFFFFEB3B)   // Moderate — yellow
        3 -> Color(0xFFFF9800)   // Unhealthy sensitive — orange
        4 -> Color(0xFFF44336)   // Unhealthy — red
        5 -> Color(0xFF9C27B0)   // Very Unhealthy — purple
        else -> Color(0xFF7B1FA2) // Hazardous — dark purple
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.label_air_quality),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = indexColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stringResource(aq.usEpaLabelResId),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = indexColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            // Main pollutants grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AqMetric("PM2.5", "${aq.pm25.toInt()} μg/m³", Modifier.weight(1f))
                AqMetric("PM10", "${aq.pm10.toInt()} μg/m³", Modifier.weight(1f))
                AqMetric("O₃", "${aq.o3.toInt()} μg/m³", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AqMetric("NO₂", "${aq.no2.toInt()} μg/m³", Modifier.weight(1f))
                AqMetric("SO₂", "${aq.so2.toInt()} μg/m³", Modifier.weight(1f))
                AqMetric("CO", "${(aq.co / 1000).let { "%.1f".format(it) }} mg/m³", Modifier.weight(1f))
            }
            // DEFRA index
            Text(
                text = stringResource(R.string.aq_defra_line, stringResource(aq.gbDefraLabelResId), aq.gbDefraIndex),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AqMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

// Keep for backward compat
@Composable
fun WeatherMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WindDirectionIndicator(degrees: Float, modifier: Modifier = Modifier) {
}
