package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.clockweather.app.R
import com.clockweather.app.domain.model.AirQuality
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter

// ─── Debug: all conditions in cycle order ─────────────────────────────────────
private val DEBUG_CONDITIONS = WeatherCondition.entries.toList()

// ─── Main content ─────────────────────────────────────────────────────────────

@Composable
fun WeatherDetailContent(
    weatherData: WeatherData,
    temperatureUnit: TemperatureUnit,
    selectedDayIndex: Int = 0,
    onDaySelected: (Int) -> Unit = {}
) {
    val forecasts = weatherData.dailyForecasts.take(7)

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
            weatherData = weatherData,
            temperatureUnit = temperatureUnit,
            selectedDayIndex = selectedDayIndex
        )

        // ── Hourly Forecast Graph ──────────────────────────────────────────
        HourlyWeatherGraph(
            hourlyForecasts = weatherData.hourlyForecasts,
            temperatureUnit = temperatureUnit
        )

        // ── 7-Day Forecast ─────────────────────────────────────────────────
        SevenDayForecastCard(
            forecasts = forecasts,
            temperatureUnit = temperatureUnit,
            selectedDayIndex = selectedDayIndex,
            onDaySelected = onDaySelected
        )

        // ── Detail metrics grid ─────────────────────────────────────────────
        MetricsGrid(weatherData = weatherData, temperatureUnit = temperatureUnit, selectedDayIndex = selectedDayIndex)

        // ── Sunrise / Sunset ────────────────────────────────────────────────
        val shownForecast = forecasts.getOrNull(selectedDayIndex) ?: forecasts.firstOrNull()
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
            else -> pluralStringResource(R.plurals.label_minutes_ago, minutes, minutes)
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
    val current = weatherData.currentWeather
    val forecasts = weatherData.dailyForecasts
    val selectedForecast = forecasts.getOrNull(selectedDayIndex)

    // ── DEBUG: tap state (must be at top to be usable in calculations)
    var debugIndex by remember { mutableIntStateOf(-1) }

    val weatherCondition = if (debugIndex >= 0) DEBUG_CONDITIONS[debugIndex]
                           else if (selectedDayIndex == 0) current.weatherCondition
                           else selectedForecast?.weatherCondition ?: current.weatherCondition

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
    val precipitationDisplay = stringResource(R.string.unit_percent, if (selectedDayIndex == 0) selectedForecast?.precipitationProbability ?: 0 else selectedForecast?.precipitationProbability ?: 0)

    val debugLabel = if (debugIndex >= 0) "🐛 ${displayCondition.name}" else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(backgroundBrush)
    ) {
        // Animated weather sprite.
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(start = 12.dp, bottom = 12.dp)
                .align(Alignment.BottomStart)
                .clickable { debugIndex = (debugIndex + 1) % DEBUG_CONDITIONS.size }
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
        
        // AAA Atmospheric Glass Overlays
        val cName = displayCondition.name.uppercase()
        when {
            cName.contains("SNOW") -> FrostOverlay()
            cName.contains("THUNDER") || cName.contains("STORM") || cName == "RAIN_HEAVY" || cName.contains("VIOLENT") -> WetGlassOverlay(intensity = 1.0f)
            cName == "RAIN_MODERATE" || cName.contains("SHOWER_MODERATE") -> WetGlassOverlay(intensity = 0.6f)
            cName == "RAIN_SLIGHT" || cName.contains("SHOWER_SLIGHT") -> WetGlassOverlay(intensity = 0.35f)
            cName.contains("DRIZZLE") -> WetGlassOverlay(intensity = 0.2f)
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

// ─── 7-Day forecast card ──────────────────────────────────────────────────────

@Composable
private fun SevenDayForecastCard(
    forecasts: List<DailyForecast>,
    temperatureUnit: TemperatureUnit,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.label_7day_forecast),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            forecasts.forEachIndexed { index, forecast ->
                ForecastDayRow(
                    forecast = forecast,
                    temperatureUnit = temperatureUnit,
                    isToday = index == 0,
                    isSelected = index == selectedDayIndex,
                    onClick = { onDaySelected(index) }
                )
                if (index < forecasts.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
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
            SmallMetric("💨", stringResource(R.string.unit_kmh, forecast.windSpeedMax))
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
private fun MetricsGrid(weatherData: WeatherData, temperatureUnit: TemperatureUnit, selectedDayIndex: Int) {
    val c = weatherData.currentWeather
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
                MetricCard(stringResource(R.string.label_metric_wind_gusts), stringResource(R.string.unit_kmh, c.windGusts), Modifier.weight(1f))
            }
        } else if (f != null) {
            // Daily forecast data
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_rain_total), stringResource(R.string.unit_mm, f.precipitationSum), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_pressure), stringResource(R.string.unit_hpa, f.averagePressure), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(stringResource(R.string.label_metric_humidity), stringResource(R.string.unit_percent, f.averageHumidity), Modifier.weight(1f))
                MetricCard(stringResource(R.string.label_metric_max_wind), stringResource(R.string.unit_kmh, f.windSpeedMax) + " " + stringResource(f.windDirectionDominant.labelResId), Modifier.weight(1f))
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
                text = "UK Air Quality: ${aq.gbDefraLabel} (DEFRA ${aq.gbDefraIndex})",
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

// ─── Freezing Frost Overlay ───────────────────────────────────────────────────

@Composable
private fun FrostOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top Left Frost Creep
        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.5f, 0f)
                lineTo(0f, h * 0.4f)
                close()
            },
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.7f), Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(w * 0.45f, h * 0.35f)
            ),
            blendMode = BlendMode.Screen
        )

        // Bottom Right Frost Creep
        drawPath(
            path = Path().apply {
                moveTo(w, h)
                lineTo(w * 0.5f, h)
                lineTo(w, h * 0.7f)
                close()
            },
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.8f), Color.Transparent),
                start = Offset(w, h),
                end = Offset(w * 0.55f, h * 0.75f)
            ),
            blendMode = BlendMode.Screen
        )
        
        // Edge Freezing Vignette - More intense
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.4f)),
                center = Offset(w / 2, h / 2),
                radius = w * 0.9f
            ),
            size = size,
            blendMode = BlendMode.Screen
        )
    }
}

// ─── Wet Glass/Camera Overlay ─────────────────────────────────────────────────

@Composable
private fun WetGlassOverlay(intensity: Float, modifier: Modifier = Modifier) {
    // intensity: 0.2 = Drizzle, 0.5 = Rain, 1.0 = Heavy/Storm
    val drops = remember(intensity) {
        val count = (50 * intensity).toInt().coerceAtLeast(10)
        List(count) {
            // Avoid extreme edges (0.05..0.95 range) to prevent clipping artifacts on rounded corners
            Offset(
                x = 0.05f + kotlin.random.Random.nextFloat() * 0.9f,
                y = 0.08f + kotlin.random.Random.nextFloat() * 0.84f // Extra margin at top/bottom
            ) to (1f + kotlin.random.Random.nextFloat() * 2.5f) // drop size
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Fixed: Use SrcOver with alpha gradient instead of Multiply 
        // to prevent black edge artifacts on rounded corners.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f * intensity)),
                center = Offset(w / 2, h / 2),
                radius = w * 1.1f
            ),
            size = size
        )

        // Static condensation / camera lens drops - AAA High Visibility
        drops.forEach { (pos, dropSize) ->
            val scale = dropSize * intensity * 1.5f // Scaled up
            val cx = pos.x * w
            val cy = pos.y * h
            
            // Drop shadow/refraction depth - Darker
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f * intensity),
                radius = scale * 2.5f,
                center = Offset(cx + 1.5f, cy + 1.5f)
            )
            
            // Hot Specular Highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * intensity),
                radius = scale * 1.6f,
                center = Offset(cx - scale * 0.7f, cy - scale * 0.7f),
                blendMode = BlendMode.Screen
            )
            
            // Inner water glow
            drawCircle(
                color = Color.White.copy(alpha = 0.25f * intensity),
                radius = scale * 1.2f,
                center = Offset(cx, cy),
                blendMode = BlendMode.Screen
            )

            // Main water drop body / Surface tension ring
            drawCircle(
                color = Color.White.copy(alpha = 0.35f * intensity),
                radius = scale * 2.5f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2f)
            )
        }
    }
}
