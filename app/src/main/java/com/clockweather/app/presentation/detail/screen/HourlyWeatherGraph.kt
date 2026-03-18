package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clockweather.app.R
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Computes "nice" round grid values for a temperature axis.
 * E.g. for 12–22 °C with targetCount=4 → [10, 15, 20, 25].
 */
private fun niceGridValues(min: Double, max: Double, targetCount: Int = 4): List<Double> {
    val range = max - min
    if (range < 0.001) return listOf(min - 2.0, min, min + 2.0)
    val rawStep = range / targetCount
    val mag = 10.0.pow(floor(log10(rawStep)))
    val niceStep = when {
        rawStep / mag <= 1.0 -> mag
        rawStep / mag <= 2.0 -> 2.0 * mag
        rawStep / mag <= 5.0 -> 5.0 * mag
        else -> 10.0 * mag
    }
    val niceMin = floor(min / niceStep) * niceStep
    val niceMax = ceil(max / niceStep) * niceStep
    return generateSequence(niceMin) { it + niceStep }
        .takeWhile { it <= niceMax + 0.001 }
        .toList()
}

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun HourlyWeatherGraph(
    hourlyForecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val next24Hours = hourlyForecasts.take(24)
    if (next24Hours.size < 2) return

    // G1: pre-compute converted temperatures so canvas Y-positions match label values
    val convertedTemps = remember(next24Hours, temperatureUnit) {
        next24Hours.map { TemperatureFormatter.convert(it.temperature, temperatureUnit) }
    }
    val rawMax = convertedTemps.max()
    val rawMin = convertedTemps.min()
    // G7: pad flat lines so a horizontal line doesn't sit at the very edge
    val paddedMin = if (rawMax - rawMin < 1.0) rawMin - 2.0 else rawMin
    val paddedMax = if (rawMax - rawMin < 1.0) rawMax + 2.0 else rawMax

    val gridValues = remember(paddedMin, paddedMax) {
        niceGridValues(paddedMin, paddedMax, targetCount = 4)
    }

    // G8: auto-scroll so the current hour is visible on first render
    val scrollState = rememberScrollState()
    val currentHourIndex = remember(next24Hours) {
        val nowHour = java.time.LocalTime.now().hour
        next24Hours.indexOfFirst { it.dateTime.hour == nowHour }.coerceAtLeast(0)
    }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        val colPx = with(density) { 60.dp.roundToPx() }
        // Show one hour before current so context is visible
        scrollState.scrollTo(((currentHourIndex - 1) * colPx).coerceAtLeast(0))
    }

    // G9: accessibility description
    val highIdx = convertedTemps.indexOf(convertedTemps.max())
    val lowIdx  = convertedTemps.indexOf(convertedTemps.min())
    val a11yDesc = "24-hour forecast: high ${convertedTemps.max().roundToInt()}° at " +
        DateFormatter.formatTime(next24Hours[highIdx].dateTime.toLocalTime(), is24Hour = true) +
        ", low ${convertedTemps.min().roundToInt()}° at " +
        DateFormatter.formatTime(next24Hours[lowIdx].dateTime.toLocalTime(), is24Hour = true)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.label_next_24_weather_forecast),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                // Fixed Y-axis temperature labels (non-scrolling)
                YAxisLabels(
                    gridValues = gridValues,
                    minTemp = paddedMin,
                    maxTemp = paddedMax,
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                )

                // Scrollable graph
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState)
                ) {
                    HourlyGraphCanvas(
                        forecasts = next24Hours,
                        convertedTemps = convertedTemps,
                        minTemp = paddedMin,
                        maxTemp = paddedMax,
                        gridValues = gridValues,
                        temperatureUnit = temperatureUnit,
                        modifier = Modifier
                            .width((next24Hours.size * 60).dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

// ── Y-axis labels (fixed, non-scrolling) ─────────────────────────────────────

@Composable
private fun YAxisLabels(
    gridValues: List<Double>,
    minTemp: Double,
    maxTemp: Double,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val topPadding    = 44.dp.toPx()
        val bottomPadding = 40.dp.toPx()
        val graphHeight   = size.height - topPadding - bottomPadding
        val tempRange     = (maxTemp - minTemp).coerceAtLeast(0.001)

        gridValues.forEach { temp ->
            val y = size.height - bottomPadding - ((temp - minTemp) / tempRange * graphHeight).toFloat()
            if (y < topPadding - 6.dp.toPx() || y > size.height - bottomPadding + 6.dp.toPx()) return@forEach

            val measured = textMeasurer.measure(
                "${temp.roundToInt()}°",
                style = TextStyle(fontSize = 9.sp, color = labelColor)
            )
            drawText(
                measured,
                topLeft = Offset(
                    x = size.width - measured.size.width - 2.dp.toPx(),
                    y = y - measured.size.height / 2f
                )
            )
        }
    }
}

// ── Scrollable graph canvas ───────────────────────────────────────────────────

@Composable
private fun HourlyGraphCanvas(
    forecasts: List<HourlyForecast>,
    convertedTemps: List<Double>,
    minTemp: Double,
    maxTemp: Double,
    gridValues: List<Double>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val primaryColor         = MaterialTheme.colorScheme.primary
    val onSurfaceColor       = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rainColor  = Color(0xFF64B5F6)
    val snowColor  = Color(0xFFBBDEFB)
    val gridColor  = onSurfaceVariantColor.copy(alpha = 0.18f)
    val tempRange  = (maxTemp - minTemp).coerceAtLeast(0.001)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = onSurfaceVariantColor
    )
    val tempLabelStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = primaryColor
    )
    val precipLabelStyle = TextStyle(fontSize = 9.sp, color = rainColor)
    val snowLabelStyle   = TextStyle(fontSize = 9.sp, color = snowColor)

    Canvas(modifier = modifier) {
        val width          = size.width
        val height         = size.height
        val pointSpacing   = width / forecasts.size
        val topPadding     = 44.dp.toPx()
        val bottomPadding  = 40.dp.toPx()
        val graphHeight    = height - topPadding - bottomPadding

        // G3: dashed horizontal grid lines
        gridValues.forEach { temp ->
            val y = height - bottomPadding - ((temp - minTemp) / tempRange * graphHeight).toFloat()
            if (y < topPadding - 4.dp.toPx() || y > height - bottomPadding + 4.dp.toPx()) return@forEach
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
            )
        }

        // G5: day/night background shading
        forecasts.forEachIndexed { index, forecast ->
            if (!forecast.isDay) {
                drawRect(
                    color = Color(0x0A000000),
                    topLeft = Offset(index * pointSpacing, topPadding),
                    size = Size(pointSpacing, graphHeight)
                )
            }
        }

        // Temperature dot positions (G1: use converted temps for Y)
        val points = forecasts.mapIndexed { index, _ ->
            val x = index * pointSpacing + pointSpacing / 2f
            val factor = (convertedTemps[index] - minTemp) / tempRange
            val y = height - bottomPadding - (factor * graphHeight).toFloat()
            Offset(x, y)
        }

        // G4: gradient fill under the temperature curve
        val fillPath = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val cp = points[i - 1].x + (points[i].x - points[i - 1].x) / 2f
                    cubicTo(cp, points[i - 1].y, cp, points[i].y, points[i].x, points[i].y)
                }
                lineTo(points.last().x, height - bottomPadding)
                lineTo(points.first().x, height - bottomPadding)
                close()
            }
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.22f), Color.Transparent),
                startY = points.minOfOrNull { it.y } ?: topPadding,
                endY = height - bottomPadding
            )
        )

        // Temperature curve (stroke)
        val strokePath = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val cp = points[i - 1].x + (points[i].x - points[i - 1].x) / 2f
                    cubicTo(cp, points[i - 1].y, cp, points[i].y, points[i].x, points[i].y)
                }
            }
        }
        drawPath(
            path = strokePath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Data-point dots
        points.forEach { center ->
            drawCircle(color = primaryColor,   radius = 3.5.dp.toPx(), center = center)
            drawCircle(color = onSurfaceColor, radius = 1.5.dp.toPx(), center = center)
        }

        // G6: rounded precipitation bars
        forecasts.forEachIndexed { index, forecast ->
            val x          = index * pointSpacing + pointSpacing / 2f
            val barWidth   = pointSpacing * 0.38f
            val probFactor = forecast.precipitationProbability / 100f
            val barHeight  = (30.dp.toPx() * probFactor).coerceAtLeast(if (probFactor > 0) 2.dp.toPx() else 0f)
            if (probFactor <= 0f) return@forEachIndexed

            val isSnow   = forecast.weatherCondition.name.contains("SNOW", ignoreCase = true)
            val barColor = if (isSnow) snowColor else rainColor
            val top      = height - bottomPadding - barHeight

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(barColor.copy(alpha = 0.72f), barColor.copy(alpha = 0.18f)),
                    startY = top, endY = height - bottomPadding
                ),
                topLeft = Offset(x - barWidth / 2f, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Temperature labels above each dot
        forecasts.forEachIndexed { index, _ ->
            val label = textMeasurer.measure(
                "${convertedTemps[index].roundToInt()}°",
                style = tempLabelStyle
            )
            val x = points[index].x - label.size.width / 2f
            val y = points[index].y - label.size.height - 4.dp.toPx()
            if (y >= 0) drawText(label, topLeft = Offset(x, y))
        }

        // Precipitation % labels
        forecasts.forEachIndexed { index, forecast ->
            if (forecast.precipitationProbability <= 0) return@forEachIndexed
            val isSnow = forecast.weatherCondition.name.contains("SNOW", ignoreCase = true)
            val label = textMeasurer.measure(
                "${forecast.precipitationProbability}%",
                style = if (isSnow) snowLabelStyle else precipLabelStyle
            )
            val probFactor = forecast.precipitationProbability / 100f
            val barHeight  = 30.dp.toPx() * probFactor
            val x = points[index].x - label.size.width / 2f
            val y = height - bottomPadding - barHeight - label.size.height - 2.dp.toPx()
            if (y >= topPadding) drawText(label, topLeft = Offset(x, y))
        }

        // Time labels at the bottom
        forecasts.forEachIndexed { index, forecast ->
            val timeStr = DateFormatter.formatTime(forecast.dateTime.toLocalTime(), is24Hour = true)
            val label = textMeasurer.measure(timeStr, style = labelStyle)
            val x = points[index].x - label.size.width / 2f
            val y = height - label.size.height - 2.dp.toPx()
            drawText(label, topLeft = Offset(x, y))
        }
    }
}
