# Improvement Tasks — Widget, Clock & Weather Graphs

> Generated: 2026-03-17  
> Covers: battery, clock accuracy, weather graph/CSV, code quality

---

## 🔋 Battery & Performance

### Task B1 — Pass `isHighPrecision` in ScreenStateReceiver (HIGH)
**File:** `ScreenStateReceiver.kt` lines 33, 55  
`scheduleNextTick(context)` is called with the default `isHighPrecision=true`, ignoring the
user's preference. On screen-on/dreaming-stopped, resolve the preference via
`(app as ClockWeatherApplication).resolveHighPrecision()` and pass it through.

### Task B2 — Pass new `enabled` value in `setHighPrecisionEnabled()` (HIGH)
**File:** `SettingsViewModel.kt` line 221  
When the user toggles high-precision OFF, `scheduleNextTick(context)` is called *after*
the DataStore write — but without passing `enabled`. The alarm is re-created with
`isHighPrecision = true` (the default). Fix: pass `enabled` directly.

### Task B3 — Wire user's `update_interval_minutes` into WeatherUpdateScheduler (MEDIUM)
**File:** `WeatherUpdateScheduler.kt` line 20  
The periodic interval is hardcoded to `30 minutes` even though the user can change
`KEY_UPDATE_INTERVAL` in settings. `setUpdateInterval()` saves the pref but never
re-schedules the worker.  
**Fix:**  
1. Add `schedule(context, intervalMinutes)` overload.  
2. In `SettingsViewModel.setUpdateInterval()`, call `WeatherUpdateScheduler.schedule(context, minutes)`.  
3. In `ClockWeatherApplication.onCreate()`, read the pref and pass it.

### Task B4 — Deduplicate concurrent weather refreshes (MEDIUM)
**File:** `WeatherRepositoryImpl.kt`  
When the cache is empty (first boot, cleared data), every widget independently calls
`weatherRepo.refreshWeatherData(location)`. This can fire 4 parallel HTTP requests for
the same location.  
**Fix:** Add a `Mutex` per location ID, or a simple `@Volatile` timestamp guard
(skip if last network call < 2 min ago).

### Task B5 — Avoid full widget rebuild for simple/static clock on every tick (MEDIUM)
**File:** `BaseWidgetUpdater.kt` lines 264-267  
`updateClockOnly()` falls through to `updateWidget(allowWeatherRefresh = false)` for the
`useSimple` path. This rebuilds the entire layout (weather, clicks, date) every minute.  
**Fix:** For `useSimple`, only push a partial update with the 4 digit views + AM/PM,
matching what the animated path already does.

### Task B6 — Gate CoroutineScope in receivers with supervisorScope + timeout (LOW)
**Files:** `ClockAlarmReceiver.kt`, `BootCompletedReceiver.kt`, `TimeChangedReceiver.kt`, `ScreenStateReceiver.kt`  
Each receiver creates `CoroutineScope(Dispatchers.Default)`. If the coroutine throws
before `pendingResult.finish()`, the BroadcastReceiver leaks. Wrap in
`supervisorScope { withTimeout(10_000) { … } }` with a `finally` block.

---

## ⏰ Clock Accuracy & Quality

### Task C1 — Use stored digits for incremental diff instead of arithmetic "previous" (HIGH)
**File:** `WidgetDataBinder.kt` lines 92–105  
The incremental path computes `prevMinute = minute - 1` arithmetically. When the
incremental window is 1–3 minutes (Doze gaps), the "previous" is wrong:  
- At 10:03 after rendering 10:00, it thinks previous was 10:02  
- So m2 (3 vs 2) flips, but m1 (0 vs 0) doesn't — even though actual last m2 was 0  

**Fix:**  
1. Store `h1, h2, m1, m2` in `WidgetClockStateStore` alongside `epochMinute`.  
2. In `bindClockViews()`, accept optional `prevH1, prevH2, prevM1, prevM2` params.  
3. `BaseWidgetUpdater` reads stored digits and passes them in.  
4. Flip ALL digits that differ from the stored values, not computed.

### Task C2 — Hide leading zero in 12-hour mode (LOW)
**Files:** `WidgetDataBinder.kt` — all three bind methods  
Hours like "09:30 AM" show a leading zero. In 12h mode, convention is to blank `h1`
when it's 0 (display " 9:30" or hide the tile entirely).  
**Fix:** When `!is24h && h1 == 0`, set the h1 ViewFlipper/TextView to blank/hidden.

### Task C3 — Optional colon blink for visual "alive" feedback (LOW)
**File:** `WidgetDataBinder.kt` or `BaseWidgetUpdater.kt`  
The colon is static. A subtle blink (toggle visibility or alpha each tick) signals the
clock is updating. This is common on real flip clocks.  
**Fix:** Track odd/even tick in state store, toggle colon alpha between 1.0 and 0.4.

---

## 📊 Weather Graph Improvements & CSV Export

All graph work is in `HourlyWeatherGraph.kt` (211 lines). Steps are ordered so each
builds on the prior one. The current implementation has a `Canvas` for drawing and a
separate `Row` overlay for text labels — these will be unified.

### Step G1 — Fix temperature unit mismatch in graph (HIGH) ★ Do First
**File:** `HourlyWeatherGraph.kt` lines 81–88, 97  
**Bug:** `minTemp`/`maxTemp` and all Y positions use raw Celsius `forecast.temperature`,
but the text labels call `TemperatureFormatter.convert()`. When Fahrenheit is selected,
dot positions and label values disagree.  
**Fix:**  
```kotlin
// Before any drawing, pre-compute converted temperatures
val convertedTemps = forecasts.map { TemperatureFormatter.convert(it.temperature, temperatureUnit) }
val maxTemp = convertedTemps.max()
val minTemp = convertedTemps.min()
val tempRange = (maxTemp - minTemp).coerceAtLeast(1.0)
```
Then use `convertedTemps[index]` everywhere Y positions are calculated (line 97):
```kotlin
val tempFactor = (convertedTemps[index] - minTemp) / tempRange
```

### Step G2 — Merge Canvas + Row into single draw pass (MEDIUM) ★ Do Second
**File:** `HourlyWeatherGraph.kt` lines 78–210  
**Problem:** The `Canvas` and `Row` are separate overlaid composables — causes overdraw
and text/point misalignment (labels don't line up with dots).  
**Fix:** Remove the `Row` entirely (lines 157–210). Use Compose `TextMeasurer` +
`drawText()` inside the Canvas block:
```kotlin
@Composable
private fun HourlyGraphCanvas(...) {
    val textMeasurer = rememberTextMeasurer()
    // ...
    Canvas(modifier = modifier) {
        // ... draw paths, dots, bars as before ...

        // Temperature label above each dot
        forecasts.forEachIndexed { index, forecast ->
            val label = textMeasurer.measure(
                "${convertedTemps[index].roundToInt()}°",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor)
            )
            drawText(label, topLeft = Offset(points[index].x - label.size.width / 2, points[index].y - label.size.height - 4.dp.toPx()))
        }

        // Time label at the bottom
        forecasts.forEachIndexed { index, forecast ->
            val timeLabel = textMeasurer.measure(
                DateFormatter.formatTime(forecast.dateTime.toLocalTime(), is24Hour = true),
                style = TextStyle(fontSize = 10.sp, color = onSurfaceVariantColor)
            )
            drawText(timeLabel, topLeft = Offset(points[index].x - timeLabel.size.width / 2, height - 16.dp.toPx()))
        }

        // Precipitation label near bars (only if > 0)
        forecasts.forEachIndexed { index, forecast ->
            if (forecast.precipitationProbability > 0) {
                val precLabel = textMeasurer.measure(
                    "${forecast.precipitationProbability}%",
                    style = TextStyle(fontSize = 9.sp, color = rainColor)
                )
                drawText(precLabel, topLeft = Offset(
                    points[index].x - precLabel.size.width / 2,
                    height - bottomPadding - barHeight - precLabel.size.height - 2.dp.toPx()
                ))
            }
        }
    }
    // No more Row overlay
}
```

### Step G3 — Add Y-axis grid lines and labels (MEDIUM)
**File:** `HourlyWeatherGraph.kt`  
**After** G1 provides converted min/max and G2 provides `textMeasurer` inside Canvas.  
**Fix:**  
1. Reserve ~32dp left padding for Y-axis labels. Shift all graph content right.
2. Compute 3–4 "nice" tick values between `minTemp` and `maxTemp`:
   ```kotlin
   fun niceGridValues(min: Double, max: Double, count: Int = 4): List<Double> {
       val step = ((max - min) / count).coerceAtLeast(1.0)
       val niceStep = // round to nearest 1, 2, or 5
       return generateSequence(floor(min / niceStep) * niceStep) { it + niceStep }
           .takeWhile { it <= max + niceStep }
           .toList()
   }
   ```
3. For each grid value, draw a dashed horizontal line:
   ```kotlin
   gridValues.forEach { temp ->
       val y = height - bottomPadding - ((temp - minTemp) / tempRange * graphHeight).toFloat()
       drawLine(
           color = onSurfaceVariantColor.copy(alpha = 0.2f),
           start = Offset(leftPadding, y),
           end = Offset(width, y),
           pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
       )
       val label = textMeasurer.measure("${temp.roundToInt()}°", style = gridLabelStyle)
       drawText(label, topLeft = Offset(2.dp.toPx(), y - label.size.height / 2))
   }
   ```

### Step G4 — Gradient fill under temperature curve (MEDIUM)
**File:** `HourlyWeatherGraph.kt`  
After drawing the stroke path, create a filled version:
```kotlin
val fillPath = Path().apply {
    addPath(path)
    lineTo(points.last().x, height - bottomPadding)
    lineTo(points.first().x, height - bottomPadding)
    close()
}
drawPath(
    path = fillPath,
    brush = Brush.verticalGradient(
        colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
        startY = points.minOf { it.y },
        endY = height - bottomPadding
    )
)
```
Draw the fill **before** the stroke path so the line sits on top.

### Step G5 — Day/night background shading (LOW)
**File:** `HourlyWeatherGraph.kt`  
`HourlyForecast.isDay` already exists. Draw subtle background shading before everything:
```kotlin
forecasts.forEachIndexed { index, forecast ->
    if (!forecast.isDay) {
        val x = index * pointSpacing
        drawRect(
            color = Color(0x0A000000),   // very subtle dark overlay
            topLeft = Offset(x, topPadding),
            size = Size(pointSpacing, graphHeight)
        )
    }
}
```
Add a small moon/sun emoji or icon in the time label row for night/day transitions.

### Step G6 — Improve precipitation bars (LOW)
**File:** `HourlyWeatherGraph.kt`  
Replace flat `drawRect` with:
```kotlin
drawRoundRect(
    brush = Brush.verticalGradient(
        listOf(barColor.copy(alpha = 0.7f), barColor.copy(alpha = 0.2f))
    ),
    topLeft = Offset(x - barWidth / 2, height - bottomPadding - barHeight),
    size = Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
    cornerRadius = CornerRadius(3.dp.toPx())
)
```

### Step G7 — Handle edge cases (LOW)
**File:** `HourlyWeatherGraph.kt`  
- **All same temp** (`tempRange ~= 0`): Add ±2° artificial padding:
  ```kotlin
  val tempRange = (maxTemp - minTemp).let { if (it < 1.0) 4.0 else it }
  val adjustedMin = if (maxTemp - minTemp < 1.0) minTemp - 2.0 else minTemp
  ```
- **< 2 entries**: Show a placeholder Text("Not enough data") instead of the Canvas.
- **Missing data**: Filter out entries with extreme sentinel values before plotting.

### Step G8 — Auto-scroll to current hour (LOW)
**File:** `HourlyWeatherGraph.kt`  
```kotlin
val scrollState = rememberScrollState()
val currentHourIndex = forecasts.indexOfFirst {
    it.dateTime.hour == java.time.LocalTime.now().hour
}.coerceAtLeast(0)

LaunchedEffect(Unit) {
    scrollState.animateScrollTo((currentHourIndex * 60.dp.toPx()).toInt())
}

Box(modifier = Modifier.horizontalScroll(scrollState)) { ... }
```

### Step G9 — Accessibility (LOW)
**File:** `HourlyWeatherGraph.kt`  
```kotlin
val highEntry = forecasts[convertedTemps.indexOf(maxTemp)]
val lowEntry = forecasts[convertedTemps.indexOf(minTemp)]
val description = "24-hour forecast: high ${maxTemp.roundToInt()}° at ${
    DateFormatter.formatTime(highEntry.dateTime.toLocalTime())
}, low ${minTemp.roundToInt()}° at ${
    DateFormatter.formatTime(lowEntry.dateTime.toLocalTime())
}"

Card(modifier = modifier.semantics { contentDescription = description }) { ... }
```

### Step G10 — CSV export / share (MEDIUM)
**New file:** `app/src/main/java/com/clockweather/app/util/WeatherCsvExporter.kt`
```kotlin
object WeatherCsvExporter {
    fun export(weatherData: WeatherData, unit: TemperatureUnit): String {
        val sb = StringBuilder()
        sb.appendLine("=== HOURLY FORECAST ===")
        sb.appendLine("DateTime,Temperature,FeelsLike,Humidity%,WindSpeed,PrecipProb%,Condition")
        weatherData.hourlyForecasts.forEach { h ->
            sb.appendLine("${h.dateTime},${TemperatureFormatter.convert(h.temperature, unit).roundToInt()},...")
        }
        sb.appendLine()
        sb.appendLine("=== DAILY FORECAST ===")
        sb.appendLine("Date,High,Low,Condition,PrecipProb%,WindMax")
        weatherData.dailyForecasts.forEach { d ->
            sb.appendLine("${d.date},${TemperatureFormatter.convert(d.temperatureMax, unit).roundToInt()},...")
        }
        return sb.toString()
    }
}
```

**Update:** `WeatherDetailScreen.kt` — add share icon in TopAppBar actions:
```kotlin
IconButton(onClick = { /* write CSV to cacheDir, share via Intent */ }) {
    Icon(Icons.Default.Share, contentDescription = "Export weather data")
}
```

**Update:** `AndroidManifest.xml` — register a `FileProvider` for cache directory sharing.

**New file:** `app/src/main/res/xml/file_paths.xml`
```xml
<paths>
    <cache-path name="csv_exports" path="exports/" />
</paths>
```

### Step G11 — Wind speed overlay (OPTIONAL — follow-up)
Secondary dashed line for wind speed with right-side Y-axis labels. Add a toggle
chip ("Show wind") in the Card header. Defer unless specifically requested.

---

## 🧹 Code Quality

### Task Q1 — Gate debug condition-cycling behind BuildConfig.DEBUG (LOW)
**File:** `CurrentWeatherCard.kt` lines 124, 199  
The `debugIndex` tap handler cycles through all `WeatherCondition` entries — this is
visible in production builds. Gate behind `if (BuildConfig.DEBUG)`.

### Task Q2 — Expand unit test coverage (LOW)
Current tests only cover `WidgetClockUpdateModeResolver`, `WidgetClockStateStore`, and
`WidgetDataBinder`. Add tests for:  
- `WidgetPrefsCache` (init, get, fallback)  
- `TemperatureFormatter.convert()` (Celsius/Fahrenheit edge cases)  
- `WeatherCsvExporter` (once created)  
- `ClockAlarmReceiver` battery tier logic (mock `BatteryManager`)

---

## Priority & Dependency Order

| Order | Task | Priority | Depends On |
|-------|------|----------|------------|
| 1 | B1 — ScreenStateReceiver isHighPrecision | 🔴 HIGH | — |
| 2 | B2 — SettingsVM isHighPrecision | 🔴 HIGH | — |
| 3 | C1 — Stored digits for incremental diff | 🔴 HIGH | — |
| 4 | G1 — Fix temperature unit in graph | 🔴 HIGH | — |
| 5 | G2 — Merge Canvas + Row (TextMeasurer) | 🟡 MED | G1 |
| 6 | G3 — Y-axis grid lines + labels | 🟡 MED | G1, G2 |
| 7 | G4 — Gradient fill under curve | 🟡 MED | G1 |
| 8 | B3 — Wire update interval to scheduler | 🟡 MED | — |
| 9 | B4 — Deduplicate weather refreshes | 🟡 MED | — |
| 10 | B5 — Avoid full rebuild for simple clock | 🟡 MED | — |
| 11 | G10 — CSV export / share | 🟡 MED | — |
| 12 | G5 — Day/night shading | 🟢 LOW | G2 |
| 13 | G6 — Improved precipitation bars | 🟢 LOW | G2 |
| 14 | G7 — Edge case handling | 🟢 LOW | G1 |
| 15 | G8 — Auto-scroll to current hour | 🟢 LOW | — |
| 16 | G9 — Accessibility | 🟢 LOW | G2 |
| 17 | B6 — Structured receiver coroutines | 🟢 LOW | — |
| 18 | C2 — Hide leading zero in 12h | 🟢 LOW | — |
| 19 | C3 — Colon blink | 🟢 LOW | — |
| 20 | Q1 — Gate debug handler | 🟢 LOW | — |
| 21 | Q2 — Unit test coverage | 🟢 LOW | G10 |
| 22 | G11 — Wind speed overlay | ⚪ OPT | G2, G3 |

