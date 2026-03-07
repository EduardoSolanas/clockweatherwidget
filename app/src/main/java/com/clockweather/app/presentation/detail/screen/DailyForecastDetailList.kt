package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            text = "7-Day Forecast",
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
                    text = forecast.weatherCondition.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = "${TemperatureFormatter.format(forecast.temperatureMax, temperatureUnit)}° / ${TemperatureFormatter.format(forecast.temperatureMin, temperatureUnit)}°",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                WeatherMetricRow("Precipitation", "${forecast.precipitationSum} mm (${forecast.precipitationProbability}%)")
                WeatherMetricRow("Humidity", "${forecast.averageHumidity}%")
                WeatherMetricRow("Pressure", "${forecast.averagePressure.toInt()} hPa")
                WeatherMetricRow("Wind max", "${forecast.windSpeedMax.toInt()} km/h ${forecast.windDirectionDominant.label}")
                WeatherMetricRow("UV Index max", forecast.uvIndexMax.toInt().toString())
                WeatherMetricRow("Sunrise", DateFormatter.formatTime(forecast.sunrise, is24Hour = true))
                WeatherMetricRow("Sunset", DateFormatter.formatTime(forecast.sunset, is24Hour = true))
                WeatherMetricRow("Daylight", DateFormatter.formatDuration(forecast.daylightDurationSeconds))
                WeatherMetricRow("Feels like", "${TemperatureFormatter.format(forecast.feelsLikeMax, temperatureUnit)}° / ${TemperatureFormatter.format(forecast.feelsLikeMin, temperatureUnit)}°")
            }
        }
    }
}

