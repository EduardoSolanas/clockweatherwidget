package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter

@Composable
fun HourlyForecastList(
    hourlyForecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "72-Hour Forecast",
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time column
            Column(modifier = Modifier.width(64.dp)) {
                Text(
                    text = DateFormatter.formatDayName(forecast.dateTime.toLocalDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateFormatter.formatTime(forecast.dateTime.toLocalTime(), is24Hour = true),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Icon
            Box(modifier = Modifier.size(32.dp).padding(horizontal = 4.dp)) {
                WeatherAnimatedIcon(condition = forecast.weatherCondition, modifier = Modifier.fillMaxSize())
            }

            // Condition
            Text(
                text = forecast.weatherCondition.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )

            // Metrics column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = TemperatureFormatter.formatWithUnit(forecast.temperature, temperatureUnit),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "💧 ${forecast.precipitationProbability}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "💨 ${forecast.windSpeed.toInt()} km/h ${forecast.windDirection.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Secondary metrics row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Humidity: ${forecast.humidity}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Dew: ${TemperatureFormatter.format(forecast.dewPoint, temperatureUnit)}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("UV: ${forecast.uvIndex.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("P: ${forecast.pressure.toInt()} hPa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

