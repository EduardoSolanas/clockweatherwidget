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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                text = "NEXT 24 HOURS",
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
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rainColor = Color(0xFF64B5F6)

    val maxTemp = forecasts.maxOf { it.temperature }
    val minTemp = forecasts.minOf { it.temperature }
    val tempRange = (maxTemp - minTemp).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val pointSpacing = width / forecasts.size
        
        // Boundaries for graph areas
        val topPadding = 40.dp.toPx()
        val bottomPadding = 40.dp.toPx()
        val graphHeight = height - topPadding - bottomPadding
        
        val points = forecasts.mapIndexed { index, forecast ->
            val x = index * pointSpacing + pointSpacing / 2
            val tempFactor = (forecast.temperature - minTemp) / tempRange
            val y = height - bottomPadding - (tempFactor * graphHeight).toFloat()
            Offset(x, y)
        }

        // Draw Temperature Path (Smoothed)
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cp1X = prev.x + (curr.x - prev.x) / 2
                    cubicTo(cp1X, prev.y, cp1X, curr.y, curr.x, curr.y)
                }
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw points and labels
        forecasts.forEachIndexed { index, forecast ->
            val center = points[index]
            
            // Temperature point
            drawCircle(
                color = primaryColor,
                radius = 4.dp.toPx(),
                center = center
            )
            drawCircle(
                color = onSurfaceColor,
                radius = 2.dp.toPx(),
                center = center
            )

            // Temperature Label
            val tempText = "${forecast.temperature.roundToInt()}°"
            // We can't use drawText easily here without a transformation or specialized scope, 
            // but for a simple agentic app we can use native canvas or just rely on the point position
            // Since we want high quality, I'll use native canvas for text if needed, 
            // but usually in Compose we use LayoutId or separate composables for labels to be easier.
            // Let's stick to simple markers.
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
                // Top Temp
                Text(
                    text = "${TemperatureFormatter.format(forecast.temperature, temperatureUnit)}°",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Bottom Time and Rain
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (forecast.precipitationProbability > 0) {
                        Text(
                            text = "${forecast.precipitationProbability}%",
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
