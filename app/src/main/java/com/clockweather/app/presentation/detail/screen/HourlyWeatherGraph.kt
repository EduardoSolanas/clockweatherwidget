package com.clockweather.app.presentation.detail.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clockweather.app.R
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import java.time.LocalDate
import kotlin.math.roundToInt

// ── Data helper (kept internal for tests) ────────────────────────────────────

internal fun scopedHourlyForecasts(
    hourlyForecasts: List<HourlyForecast>,
    selectedDate: LocalDate?
): List<HourlyForecast> {
    if (hourlyForecasts.isEmpty()) return emptyList()

    val orderedForecasts = hourlyForecasts.sortedBy { it.dateTime }
    if (selectedDate == null) {
        return orderedForecasts.take(24)
    }

    val filtered = orderedForecasts.filter { it.dateTime.toLocalDate() == selectedDate }
    // If fewer than 2 data points remain for the selected date (e.g. late evening at 23:00),
    // fall back to the next 24 hours so the graph is never hidden.
    return if (filtered.size >= 2) filtered else orderedForecasts.take(24)
}

// ── Curve colour (warm amber — matches reference) ─────────────────────────────

private val CurveColor  = Color(0xFFFFA040)
private val PrecipColor = Color(0xFF64B5F6)
private val CurrentHourHighlight = Color.White.copy(alpha = 0.18f)
private val CurrentHourOutline = Color.White.copy(alpha = 0.30f)

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun HourlyWeatherGraph(
    hourlyForecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null
) {
    val hours = remember(hourlyForecasts, selectedDate) {
        scopedHourlyForecasts(hourlyForecasts, selectedDate)
    }
    if (hours.size < 2) return

    val convertedTemps = remember(hours, temperatureUnit) {
        hours.map { TemperatureFormatter.convert(it.temperature, temperatureUnit) }
    }

    val rawMin  = convertedTemps.min()
    val rawMax  = convertedTemps.max()
    val spread  = (rawMax - rawMin).coerceAtLeast(4.0)
    // Extra headroom above peak so temp labels don't clip; less below.
    val padMin  = rawMin - spread * 0.10
    val padMax  = rawMax + spread * 0.30

    val resolvedDate   = selectedDate ?: hours.first().dateTime.toLocalDate()
    val isTodayView    = resolvedDate == LocalDate.now()

    val scrollState = rememberScrollState()
    val density     = LocalDensity.current

    // Auto-scroll: bring current hour into view (today only)
    val currentIdx = remember(hours, isTodayView) {
        if (!isTodayView) 0
        else {
            val nowHour = java.time.LocalTime.now().hour
            hours.indexOfFirst { it.dateTime.hour == nowHour }.coerceAtLeast(0)
        }
    }
    LaunchedEffect(currentIdx, hours.size, isTodayView) {
        val colPx    = with(density) { ColW.roundToPx() }
        val targetPx = ((currentIdx - 1).coerceAtLeast(0) * colPx)
        scrollState.scrollTo(targetPx)
    }

    // Capture colours before entering canvas scope
    val onSurface        = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer     = rememberTextMeasurer()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(24.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = stringResource(R.string.label_hourly_forecast).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = "(${hours.size}h)  →",
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Scrollable body ───────────────────────────────────────────────
            val totalWidth = ColW * hours.size

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Column(modifier = Modifier.width(totalWidth)) {

                    // Time labels
                    Box(modifier = Modifier.width(totalWidth)) {
                        if (isTodayView && currentIdx in hours.indices) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val colPx = ColW.toPx()
                                val sideInset = 2.dp.toPx()
                                val left = currentIdx * colPx + sideInset
                                val width = (colPx - sideInset * 2f).coerceAtLeast(0f)
                                drawRoundRect(
                                    color = CurrentHourHighlight,
                                    topLeft = Offset(left, 0f),
                                    size = Size(width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = CurrentHourOutline,
                                    topLeft = Offset(left, 0f),
                                    size = Size(width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        Column(modifier = Modifier.width(totalWidth)) {
                            Row(modifier = Modifier.width(totalWidth)) {
                                hours.forEachIndexed { i, hour ->
                                    val isCurrent = isTodayView && i == currentIdx
                                    Text(
                                        text      = DateFormatter.formatTime(hour.dateTime.toLocalTime(), is24Hour = true),
                                        style     = MaterialTheme.typography.labelSmall,
                                        fontSize  = 12.sp,
                                        color     = if (isCurrent) onSurface else onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier  = Modifier
                                            .width(ColW)
                                            .padding(vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(2.dp))

                            Canvas(
                                modifier = Modifier
                                    .width(totalWidth)
                                    .height(GraphH)
                            ) {
                                val w         = size.width
                                val h         = size.height
                                val colPx     = w / hours.size
                                val tempRange = (padMax - padMin).coerceAtLeast(0.001)
                                val topPad    = 22.dp.toPx()
                                val botPad    = 6.dp.toPx()
                                val graphH    = h - topPad - botPad

                                val pts = convertedTemps.mapIndexed { i, temp ->
                                    val x = i * colPx + colPx / 2f
                                    val y = h - botPad - ((temp - padMin) / tempRange * graphH).toFloat()
                                    Offset(x, y)
                                }

                                for (i in 1 until hours.size) {
                                    drawLine(
                                        color       = Color.White.copy(alpha = 0.10f),
                                        start       = Offset(i * colPx, topPad),
                                        end         = Offset(i * colPx, h - botPad),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                val fillPath = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) {
                                        val cp = pts[i - 1].x + (pts[i].x - pts[i - 1].x) / 2f
                                        cubicTo(cp, pts[i - 1].y, cp, pts[i].y, pts[i].x, pts[i].y)
                                    }
                                    lineTo(pts.last().x, h - botPad)
                                    lineTo(pts.first().x, h - botPad)
                                    close()
                                }
                                val minY = pts.minOf { it.y }
                                drawPath(
                                    path  = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(CurveColor.copy(alpha = 0.28f), Color.Transparent),
                                        startY = minY,
                                        endY   = h - botPad
                                    )
                                )

                                val strokePath = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) {
                                        val cp = pts[i - 1].x + (pts[i].x - pts[i - 1].x) / 2f
                                        cubicTo(cp, pts[i - 1].y, cp, pts[i].y, pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path  = strokePath,
                                    color = CurveColor,
                                    style = Stroke(
                                        width = 2.5.dp.toPx(),
                                        cap   = StrokeCap.Round,
                                        join  = StrokeJoin.Round
                                    )
                                )

                                pts.forEachIndexed { i, pt ->
                                    val isCurrent = isTodayView && i == currentIdx
                                    drawCircle(CurveColor, radius = if (isCurrent) 5.dp.toPx() else 3.5.dp.toPx(), center = pt)
                                    drawCircle(Color.White.copy(alpha = 0.9f), radius = if (isCurrent) 2.5.dp.toPx() else 1.5.dp.toPx(), center = pt)
                                }

                                convertedTemps.forEachIndexed { i, temp ->
                                    val isCurrent = isTodayView && i == currentIdx
                                    val label = textMeasurer.measure(
                                        "${temp.roundToInt()}°",
                                        style = TextStyle(
                                            fontSize   = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = if (isCurrent) CurveColor else Color.White
                                        )
                                    )
                                    val lx = pts[i].x - label.size.width / 2f
                                    val ly = pts[i].y - label.size.height - 4.dp.toPx()
                                    if (ly >= 0f) drawText(label, topLeft = Offset(lx, ly))
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.width(totalWidth),
                                color    = onSurface.copy(alpha = 0.12f)
                            )

                            Row(modifier = Modifier.width(totalWidth)) {
                                hours.forEachIndexed { i, hour ->
                                    val isCurrent   = isTodayView && i == currentIdx
                                    val labelColor  = if (isCurrent) CurveColor else onSurface

                                    Column(
                                        modifier            = Modifier
                                            .width(ColW)
                                            .height(96.dp)
                                            .padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier         = Modifier.size(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(hour.weatherCondition.iconResId),
                                                contentDescription = stringResource(hour.weatherCondition.labelResId),
                                                tint = Color.Unspecified,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Spacer(Modifier.height(2.dp))

                                        Box(
                                            modifier         = Modifier
                                                .height(32.dp)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Text(
                                                text      = stringResource(hour.weatherCondition.labelResId),
                                                style     = MaterialTheme.typography.labelSmall,
                                                fontSize  = 9.sp,
                                                color     = labelColor,
                                                textAlign = TextAlign.Center,
                                                maxLines  = 2,
                                                overflow  = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(Modifier.weight(1f))

                                        Text(
                                            text       = "${hour.precipitationProbability}%",
                                            style      = MaterialTheme.typography.labelSmall,
                                            fontSize   = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color      = if (hour.precipitationProbability > 0) PrecipColor
                                                         else onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

private val ColW   = 46.dp   // column width — ~7 visible at once on a 360dp screen
private val GraphH = 128.dp  // temperature graph canvas height
