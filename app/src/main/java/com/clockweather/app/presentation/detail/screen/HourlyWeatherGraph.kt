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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clockweather.app.R
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.util.DateFormatter
import com.clockweather.app.util.TemperatureFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

// ── Data helper (kept internal for tests) ────────────────────────────────────

internal fun scopedHourlyForecasts(
    hourlyForecasts: List<HourlyForecast>,
    selectedDate: LocalDate?
): List<HourlyForecast> = scopedHourlyForecasts(
    hourlyForecasts = hourlyForecasts,
    selectedDate = selectedDate,
    referenceDateTime = LocalDateTime.now()
)

internal fun scopedHourlyForecasts(
    hourlyForecasts: List<HourlyForecast>,
    selectedDate: LocalDate?,
    referenceDateTime: LocalDateTime
): List<HourlyForecast> {
    if (hourlyForecasts.isEmpty()) return emptyList()

    val orderedForecasts = hourlyForecasts.sortedBy { it.dateTime }
    if (selectedDate == null) {
        return orderedForecasts.take(24)
    }

    // Today behaves as a rolling 24-hour window from the current hour so late-evening
    // views still show a full next-24h forecast across midnight.
    val referenceHour = referenceDateTime.withMinute(0).withSecond(0).withNano(0)
    if (selectedDate == referenceHour.toLocalDate()) {
        val rollingStartIndex = orderedForecasts.indexOfFirst { !it.dateTime.isBefore(referenceHour) }
        if (rollingStartIndex >= 0) {
            return orderedForecasts.drop(rollingStartIndex).take(24)
        }
    }

    val filtered = orderedForecasts.filter { it.dateTime.toLocalDate() == selectedDate }
    // For non-today dates, keep the selected calendar day when possible. If fewer than 2
    // points remain (e.g. sparse backend data or a late single-hour carry-over), fall
    // forward to the next 24 hours so the graph is never hidden.
    if (filtered.size >= 2) {
        return filtered
    }

    if (filtered.isEmpty()) {
        return orderedForecasts.take(24)
    }

    val selectedStartIndex = orderedForecasts.indexOfFirst {
        it.dateTime.toLocalDate() == selectedDate
    }

    return if (selectedStartIndex >= 0) {
        orderedForecasts.drop(selectedStartIndex).take(24)
    } else {
        orderedForecasts.take(24)
    }
}

// ── Curve colour (warm amber — matches reference) ─────────────────────────────

private val CurveColor  = Color(0xFFFFA040)
private val PrecipColor = Color(0xFF64B5F6)
// Current-hour selection: border-only, matching the 14-day selected column style.
private val CurrentHourBorderColor  = Color.White.copy(alpha = 0.92f)
private val CurrentHourBorderWidth  = 2.dp

// ── Current-hour index helper (kept internal for tests) ──────────────────────

internal fun resolveCurrentHourIndex(hours: List<HourlyForecast>, nowHour: Int): Int =
    hours.indexOfFirst { it.dateTime.hour == nowHour }.coerceAtLeast(0)

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun HourlyWeatherGraph(
    hourlyForecasts: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null
) {
    val (hours, referenceDateTime) = remember(hourlyForecasts, selectedDate) {
        val ref = LocalDateTime.now()
        scopedHourlyForecasts(hourlyForecasts, selectedDate, ref) to ref
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
    val isTodayView    = resolvedDate == referenceDateTime.toLocalDate()
    val subtitleText = if (isTodayView) {
        "${stringResource(R.string.label_today)} · ${hours.size}h"
    } else {
        "${DateFormatter.formatDate(resolvedDate)} · ${hours.size}h"
    }

    val scrollState = rememberScrollState()
    val density     = LocalDensity.current
    val layoutMetrics = remember(hours.size, density) {
        val columnWidthPx = with(density) { ColW.roundToPx() }
        HourlyGraphLayoutMetrics(
            columnWidthPx = columnWidthPx,
            columnWidthDp = with(density) { columnWidthPx.toDp() },
            totalWidthDp = with(density) { (columnWidthPx * hours.size).toDp() }
        )
    }

    // Auto-scroll: bring current hour into view (today only)
    val currentIdx = remember(hours, isTodayView) {
        if (!isTodayView) 0
        else resolveCurrentHourIndex(hours, referenceDateTime.toLocalTime().hour)
    }
    LaunchedEffect(currentIdx, hours.size, isTodayView) {
        val targetPx = ((currentIdx - 1).coerceAtLeast(0) * layoutMetrics.columnWidthPx)
        scrollState.scrollTo(targetPx)
    }

    // Capture colours before entering canvas scope
    val onSurface        = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer     = rememberTextMeasurer()
    val selectionHorizontalInsetPx = with(density) { CurrentHourSelectionHorizontalInset.roundToPx() }
    val selectionOverlayMetrics = remember(hours.size, currentIdx, isTodayView, layoutMetrics, selectionHorizontalInsetPx) {
        currentHourSelectionOverlayMetrics(
            currentIdx = currentIdx,
            hoursCount = hours.size,
            columnWidthPx = layoutMetrics.columnWidthPx,
            horizontalInsetPx = selectionHorizontalInsetPx
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = stringResource(R.string.label_hourly_forecast),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            val totalWidth = layoutMetrics.totalWidthDp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(HourlyGraphPanelHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(GraphPanelCornerRadius))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(GraphPanelCornerRadius)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(GraphPanelCornerRadius))
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val colPx = layoutMetrics.columnWidthPx.toFloat()
                            val fullHeight = size.height

                            hours.indices.forEach { index ->
                                if (index % 2 == 0) {
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.025f),
                                        topLeft = Offset(layoutMetrics.leftPx(index), 0f),
                                        size = Size(colPx, fullHeight)
                                    )
                                }
                            }

                            for (index in 1 until hours.size) {
                                if (isTodayView && currentIdx in hours.indices && (index == currentIdx || index == currentIdx + 1)) {
                                    continue
                                }
                                val x = layoutMetrics.leftPx(index)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(x, 10.dp.toPx()),
                                    end = Offset(x, fullHeight - 10.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }

                        Column(modifier = Modifier.width(totalWidth)) {
                            Row(
                                modifier = Modifier
                                    .width(totalWidth)
                                    .height(TimeRowH)
                            ) {
                                hours.forEachIndexed { index, hour ->
                                    HourlyTimeSlice(
                                        hour = hour,
                                        columnWidth = layoutMetrics.columnWidthDp,
                                        showDayMarker = index == 0 || hour.dateTime.toLocalDate() != hours[index - 1].dateTime.toLocalDate(),
                                        isCurrent = isTodayView && index == currentIdx,
                                        onSurface = onSurface,
                                        onSurfaceVariant = onSurfaceVariant
                                    )
                                }
                            }

                            Canvas(
                                modifier = Modifier
                                    .width(totalWidth)
                                    .height(GraphH)
                            ) {
                                val h         = size.height
                                val tempRange = (padMax - padMin).coerceAtLeast(0.001)
                                val topPad    = 28.dp.toPx()
                                val botPad    = 12.dp.toPx()
                                val graphH    = h - topPad - botPad

                                val pts = convertedTemps.mapIndexed { i, temp ->
                                    val x = layoutMetrics.centerPx(i)
                                    val y = h - botPad - ((temp - padMin) / tempRange * graphH).toFloat()
                                    Offset(x, y)
                                }

                                val baselineY = h - botPad

                                val fillPath = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) {
                                        val cp = pts[i - 1].x + (pts[i].x - pts[i - 1].x) / 2f
                                        cubicTo(cp, pts[i - 1].y, cp, pts[i].y, pts[i].x, pts[i].y)
                                    }
                                    lineTo(pts.last().x, baselineY)
                                    lineTo(pts.first().x, baselineY)
                                    close()
                                }
                                val minY = pts.minOf { it.y }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(CurveColor.copy(alpha = 0.24f), Color.Transparent),
                                        startY = minY,
                                        endY = baselineY
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
                                    path = strokePath,
                                    color = CurveColor,
                                    style = Stroke(
                                        width = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )

                                pts.forEachIndexed { index, pt ->
                                    val isCurrent = isTodayView && index == currentIdx
                                    drawCircle(
                                        color = CurveColor,
                                        radius = if (isCurrent) 5.dp.toPx() else 3.5.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.92f),
                                        radius = if (isCurrent) 2.6.dp.toPx() else 1.6.dp.toPx(),
                                        center = pt
                                    )
                                }

                                convertedTemps.forEachIndexed { index, temp ->
                                    val isCurrent = isTodayView && index == currentIdx
                                    val label = textMeasurer.measure(
                                        text = "${temp.roundToInt()}°",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCurrent) CurveColor else Color.White
                                        )
                                    )
                                    val lx = pts[index].x - label.size.width / 2f
                                    val ly = (pts[index].y - label.size.height - 6.dp.toPx()).coerceAtLeast(4.dp.toPx())
                                    drawText(label, topLeft = Offset(lx, ly))
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .width(totalWidth)
                                    .height(BottomSliceH)
                            ) {
                                hours.forEachIndexed { index, hour ->
                                    HourlyMetaSlice(
                                        hour = hour,
                                        columnWidth = layoutMetrics.columnWidthDp,
                                        isCurrent = isTodayView && index == currentIdx,
                                        onSurface = onSurface,
                                        onSurfaceVariant = onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (isTodayView && selectionOverlayMetrics != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(vertical = CurrentHourSelectionVerticalInset)
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(selectionOverlayMetrics.offsetXPx, 0) }
                                    .width(with(density) { selectionOverlayMetrics.widthPx.toDp() })
                                    .fillMaxHeight()
                                    .border(
                                        width = CurrentHourBorderWidth,
                                        color = CurrentHourBorderColor,
                                        shape = RoundedCornerShape(CurrentHourSelectionCornerRadius)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyTimeSlice(
    hour: HourlyForecast,
    columnWidth: Dp,
    showDayMarker: Boolean,
    isCurrent: Boolean,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Column(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showDayMarker) {
            Text(
                text = DateFormatter.formatDayName(hour.dateTime.toLocalDate()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = if (isCurrent) onSurface else onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = DateFormatter.formatTime(hour.dateTime.toLocalTime(), is24Hour = true),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = if (isCurrent) onSurface else onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HourlyMetaSlice(
    hour: HourlyForecast,
    columnWidth: Dp,
    isCurrent: Boolean,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    val labelColor = if (isCurrent) CurveColor else onSurface

    Column(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(hour.weatherCondition.iconResId),
                contentDescription = stringResource(hour.weatherCondition.labelResId),
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(hour.weatherCondition.labelResId),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.unit_percent, hour.precipitationProbability),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (hour.precipitationProbability > 0) PrecipColor else onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal data class CurrentHourSelectionOverlayMetrics(
    val offsetXPx: Int,
    val widthPx: Int
)

internal fun currentHourSelectionOverlayMetrics(
    currentIdx: Int,
    hoursCount: Int,
    columnWidthPx: Int,
    horizontalInsetPx: Int = 0
): CurrentHourSelectionOverlayMetrics? {
    if (currentIdx !in 0 until hoursCount) return null

    val safeInsetPx = horizontalInsetPx.coerceAtLeast(0)
    val isFirst = currentIdx == 0
    val isLast = currentIdx == hoursCount - 1
    val adjustedOffset = if (isFirst) safeInsetPx else currentIdx * columnWidthPx
    val adjustedWidth = when {
        isFirst && isLast -> (columnWidthPx - safeInsetPx * 2).coerceAtLeast(0)
        isFirst || isLast -> (columnWidthPx - safeInsetPx).coerceAtLeast(0)
        else -> columnWidthPx
    }

    return CurrentHourSelectionOverlayMetrics(
        offsetXPx = adjustedOffset,
        widthPx = adjustedWidth
    )
}

private data class HourlyGraphLayoutMetrics(
    val columnWidthPx: Int,
    val columnWidthDp: Dp,
    val totalWidthDp: Dp
) {
    fun leftPx(index: Int): Float = index * columnWidthPx.toFloat()

    fun centerPx(index: Int): Float = leftPx(index) + columnWidthPx / 2f
}

// ── Constants ─────────────────────────────────────────────────────────────────

private val ColW = 58.dp
private val GraphPanelCornerRadius = 22.dp
private val CurrentHourSelectionCornerRadius = 12.dp
private val CurrentHourSelectionHorizontalInset = 2.dp
private val CurrentHourSelectionVerticalInset = 2.dp
private val TimeRowH = 52.dp
private val GraphH = 148.dp
private val BottomSliceH = 112.dp
private val HourlyGraphPanelHeight = TimeRowH + GraphH + BottomSliceH
