package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clockweather.app.R
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import kotlin.math.roundToInt

@Composable
fun HourlyWeatherGraph(
    hourlyForecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val next24Hours = hourlyForecasts.take(24)
    if (next24Hours.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.label_next_24_weather_forecast),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                HourlyGraphCanvas(
                    forecasts = next24Hours,
                    temperatureUnit = temperatureUnit,
                    modifier = Modifier
                        .width((24 * 60).dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun HourlyGraphCanvas(
    forecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rainColor = Color(0xFF64B5F6)

    val maxTemp = forecasts.maxOf { maxOf(it.temperature, it.feelsLike) }
    val minTemp = forecasts.minOf { minOf(it.temperature, it.feelsLike) }
    val tempRange = (maxTemp - minTemp).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val pointSpacing = width / forecasts.size
        
        // Boundaries for graph areas
        val topPadding = 45.dp.toPx()
        val bottomPadding = 40.dp.toPx()
        val graphHeight = height - topPadding - bottomPadding
        
        val actualPoints = forecasts.mapIndexed { index, forecast ->
            val x = index * pointSpacing + pointSpacing / 2
            val tempFactor = (forecast.temperature - minTemp) / tempRange
            val y = height - bottomPadding - (tempFactor * graphHeight).toFloat()
            Offset(x, y)
        }

        val feelsLikePoints = forecasts.mapIndexed { index, forecast ->
            val x = index * pointSpacing + pointSpacing / 2
            val tempFactor = (forecast.feelsLike - minTemp) / tempRange
            val y = height - bottomPadding - (tempFactor * graphHeight).toFloat()
            Offset(x, y)
        }

        // Draw Actual Temperature Path
        val actualPath = Path().apply {
            if (actualPoints.isNotEmpty()) {
                moveTo(actualPoints[0].x, actualPoints[0].y)
                for (i in 1 until actualPoints.size) {
                    val prev = actualPoints[i - 1]
                    val curr = actualPoints[i]
                    val cp1X = prev.x + (curr.x - prev.x) / 2
                    cubicTo(cp1X, prev.y, cp1X, curr.y, curr.x, curr.y)
                }
            }
        }

        // Draw Feels Like Temperature Path
        val feelsLikePath = Path().apply {
            if (feelsLikePoints.isNotEmpty()) {
                moveTo(feelsLikePoints[0].x, feelsLikePoints[0].y)
                for (i in 1 until feelsLikePoints.size) {
                    val prev = feelsLikePoints[i - 1]
                    val curr = feelsLikePoints[i]
                    val cp1X = prev.x + (curr.x - prev.x) / 2
                    cubicTo(cp1X, prev.y, cp1X, curr.y, curr.x, curr.y)
                }
            }
        }

        // Draw Feels Like Line First (Lower/Secondary)
        drawPath(
            path = feelsLikePath,
            color = secondaryColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )

        // Draw Actual Line (Top/Primary)
        drawPath(
            path = actualPath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw points for actual temperature
        forecasts.forEachIndexed { index, _ ->
            val center = actualPoints[index]
            drawCircle(
                color = primaryColor,
                radius = 3.5.dp.toPx(),
                center = center
            )
            drawCircle(
                color = onSurfaceColor,
                radius = 1.5.dp.toPx(),
                center = center
            )
        }
        
        // Draw Precipitation Bars at the bottom
        forecasts.forEachIndexed { index, forecast ->
            val x = index * pointSpacing + pointSpacing / 2
            val barWidth = pointSpacing * 0.4f
            val probFactor = forecast.precipitationProbability / 100f
            val barHeight = 30.dp.toPx() * probFactor
            
            if (probFactor > 0) {
                drawRect(
                    color = rainColor.copy(alpha = 0.6f),
                    topLeft = Offset(x - barWidth / 2, height - bottomPadding - barHeight),
                    size = Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx()))
                )
            }
        }
    }
    
    // Transparent labels layer using absolute positioning or just align them in a row
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        forecasts.forEach { forecast ->
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Temps
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = stringResource(
                            R.string.unit_celsius,
                            TemperatureFormatter.convert(forecast.temperature, temperatureUnit)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Text(
                        text = stringResource(
                            R.string.unit_celsius,
                            TemperatureFormatter.convert(forecast.feelsLike, temperatureUnit)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Bottom Time and Rain
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (forecast.precipitationProbability > 0) {
                        Text(
                            text = stringResource(R.string.unit_percent, forecast.precipitationProbability),
                            style = MaterialTheme.typography.labelSmall,
                            color = rainColor,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = DateFormatter.formatTime(forecast.dateTime.toLocalTime(), is24Hour = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariantColor
                    )
                }
            }
        }
    }
}
