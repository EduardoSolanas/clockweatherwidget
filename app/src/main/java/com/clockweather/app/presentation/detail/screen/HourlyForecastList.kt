package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clockweather.app.R
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter

private val HourlyForecastRowMinHeight = 88.dp
private val HourlyForecastTimeSliceWidth = 72.dp
private val HourlyForecastIconSliceWidth = 52.dp
private val HourlyForecastTemperatureSliceWidth = 72.dp

@Composable
fun HourlyForecastList(
    hourlyForecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_72_hour_forecast),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        // Show next 72 hours
        hourlyForecasts.take(72).forEach { forecast ->
            HourlyForecastItem(forecast = forecast, temperatureUnit = temperatureUnit)
        }
    }
}

@Composable
fun HourlyForecastItem(forecast: HourlyForecast, temperatureUnit: TemperatureUnit) {
    val temperatureLabel = "${TemperatureFormatter.format(forecast.temperature, temperatureUnit)}°"
    val windLabel = stringResource(
        R.string.label_wind_speed_kmh,
        forecast.windSpeed.toInt(),
        stringResource(forecast.windDirection.labelResId)
    )
    val humidityLabel = stringResource(R.string.label_metric_humidity_short, forecast.humidity)
    val uvLabel = stringResource(R.string.label_metric_uv_short, forecast.uvIndex.toInt())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HourlyForecastRowMinHeight)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(HourlyForecastTimeSliceWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = DateFormatter.formatDayName(forecast.dateTime.toLocalDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = DateFormatter.formatTime(forecast.dateTime.toLocalTime(), is24Hour = true),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(HourlyForecastIconSliceWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                WeatherAnimatedIcon(
                    condition = forecast.weatherCondition,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(forecast.weatherCondition.labelResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HourlyForecastMetric(
                        text = windLabel,
                        modifier = Modifier.weight(1.35f)
                    )
                    HourlyForecastMetric(
                        text = humidityLabel,
                        modifier = Modifier.weight(1f)
                    )
                    HourlyForecastMetric(
                        text = uvLabel,
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(HourlyForecastTemperatureSliceWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = temperatureLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.label_precip_percent, forecast.precipitationProbability),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastMetric(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

