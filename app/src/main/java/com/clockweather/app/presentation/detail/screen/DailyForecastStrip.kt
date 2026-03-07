package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.presentation.detail.theme.WeekendYellow
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import java.time.DayOfWeek

@Composable
fun DailyForecastStrip(
    dailyForecasts: List<DailyForecast>,
    temperatureUnit: TemperatureUnit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dailyForecasts.take(7).forEach { forecast ->
                DailyStripItem(forecast = forecast, temperatureUnit = temperatureUnit)
            }
        }
    }
}

@Composable
private fun DailyStripItem(forecast: DailyForecast, temperatureUnit: TemperatureUnit) {
    val isWeekend = forecast.date.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val textColor = if (isWeekend) WeekendYellow else MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = DateFormatter.formatDayName(forecast.date),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = DateFormatter.formatDayShort(forecast.date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.size(24.dp)) {
            WeatherAnimatedIcon(condition = forecast.weatherCondition, modifier = Modifier.fillMaxSize())
        }
        if (forecast.precipitationProbability > 0) {
            Text(
                text = "${forecast.precipitationProbability}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = TemperatureFormatter.format(forecast.temperatureMax, temperatureUnit) + "°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = TemperatureFormatter.format(forecast.temperatureMin, temperatureUnit) + "°",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

