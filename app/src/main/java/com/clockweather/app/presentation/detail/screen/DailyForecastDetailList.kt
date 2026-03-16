package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clockweather.app.R
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter

@Composable
fun DailyForecastDetailList(
    dailyForecasts: List<DailyForecast>,
    temperatureUnit: TemperatureUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_7_day_forecast_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        dailyForecasts.take(7).forEach { forecast ->
            DailyForecastDetailItem(forecast = forecast, temperatureUnit = temperatureUnit)
        }
    }
}

@Composable
fun DailyForecastDetailItem(forecast: DailyForecast, temperatureUnit: TemperatureUnit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = DateFormatter.formatDayName(forecast.date),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = DateFormatter.formatDayShort(forecast.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(id = forecast.weatherCondition.labelResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = stringResource(if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit, forecast.temperatureMax) + 
                           " / " + 
                           stringResource(if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit, forecast.temperatureMin),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                WeatherMetricRow(stringResource(R.string.label_precipitation), stringResource(R.string.unit_mm, forecast.precipitationSum) + " " + stringResource(R.string.unit_percent, forecast.precipitationProbability))
                WeatherMetricRow(stringResource(R.string.label_humidity), stringResource(R.string.unit_percent, forecast.averageHumidity))
                WeatherMetricRow(stringResource(R.string.label_pressure), stringResource(R.string.unit_hpa, forecast.averagePressure))
                WeatherMetricRow(stringResource(R.string.label_metric_wind_max), stringResource(R.string.unit_kmh, forecast.windSpeedMax) + " " + stringResource(forecast.windDirectionDominant.labelResId))
                WeatherMetricRow(stringResource(R.string.label_metric_uv_index_max), forecast.uvIndexMax.toInt().toString())
                WeatherMetricRow(stringResource(R.string.label_sunrise), DateFormatter.formatTime(forecast.sunrise, is24Hour = true))
                WeatherMetricRow(stringResource(R.string.label_sunset), DateFormatter.formatTime(forecast.sunset, is24Hour = true))
                WeatherMetricRow(stringResource(R.string.label_metric_daylight), DateFormatter.formatDuration(forecast.daylightDurationSeconds))
                WeatherMetricRow(stringResource(R.string.label_metric_feels_like), 
                    stringResource(if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit, forecast.feelsLikeMax) + " / " + 
                    stringResource(if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit, forecast.feelsLikeMin))
            }
        }
    }
}

