# Weather & Clock Widget Refresh Improvements

This document outlines the architecture of the widget refresh and time/temperature rendering systems in Clock & Weather, compares **Pre-Android 12 (API < 31)** and **Post-Android 12 (API ≥ 31)** implementations, and details concrete improvements for data accuracy, visual scaling, and battery efficiency.

---

## 1. Current Architecture Overview

```
                      ┌─────────────────────────────────┐
                      │    Trigger Sources              │
                      │  • WorkManager (Periodic / User)│
                      │  • Screen Wake (Unlock)         │
                      │  • Passive Location (5km Delta) │
                      │  • Settings / Preference Change │
                      └────────────────┬────────────────┘
                                       │
                                       ▼
                      ┌─────────────────────────────────┐
                      │    WeatherRepositoryImpl        │
                      │  • ensureFreshWeatherData()     │
                      │  • Cache Freshness Verification │
                      └────────────────┬────────────────┘
                                       │
                                       ▼
                      ┌─────────────────────────────────┐
                      │    BaseWidgetUpdater            │
                      │  • Reads DataStore & Cache      │
                      │  • Formats Digits & Weather     │
                      │  • Builds RemoteViews           │
                      └────────────────┬────────────────┘
                                       │
                                       ▼
                      ┌─────────────────────────────────┐
                      │    AppWidgetManager             │
                      │  • updateAppWidget()            │
                      │  • Launcher Hosts Render Views  │
                      └─────────────────────────────────┘
```

---

## 2. Pre-Android 12 vs. Post-Android 12 Comparison

| Dimension | Pre-Android 12 (API < 31) | Post-Android 12 (API ≥ 31) |
| :--- | :--- | :--- |
| **Clock Rendering** | 4 individual `TextClock` views inside 29dp clipped `FrameLayout`s. Uses gravity clipping (`left` for tens, `right` for units) to extract single digits. | 2 spanning `TextClock` views (`clock_hour`, `clock_minute`) with dynamically calculated `setLetterSpacing` across the flip tiles. |
| **Tile Sizing & Font Scale** | Fixed dimensions (48dp). `setViewLayoutHeight` and dynamic `setTextViewTextSize` are unsupported via `RemoteViews`. | Dynamic runtime layout resizing (`setViewLayoutHeight`) and font scaling based on user preferences (Small, Medium, Large, XL). |
| **Weather Card Placement** | Uses `@+id/weather_icon_top` (`layout_marginTop="-56dp"`) to prevent older launcher hosts from floating the weather card too far below the clock. | Uses `@+id/weather_icon` (`layout_gravity="bottom"`), naturally adapted to modern grid allocations. |
| **Weather Icon Safety** | Pre-rendered in-process to 192px ARGB_8888 `Bitmap`s to avoid crashes in launcher vector inflaters (e.g. MIUI / TouchWiz gradient XML parser bugs). | Same pre-rendered bitmap path for consistency and low memory footprint. |
| **Responsive Resizing** | Host re-measures on broadcast update; single static layout per widget provider. | Capable of supporting `RemoteViews(Map<SizeF, RemoteViews>)` multi-size responsive layouts. |

---

## 3. High-Impact Improvement Areas

### 1. Real-Time Temperature Progression & Hourly Interpolation
* **Problem**: Currently, `currentDisplayWeather()` returns the exact temperature snapshot recorded at the last network fetch. If the refresh frequency is 45–60 minutes, the displayed temperature does not reflect changes between updates.
* **Proposed Solution**:
  * When local redraw triggers occur (such as screen wake or hourly broadcasts), evaluate the location's `hourlyForecasts`.
  * Interpolate between the current hour and next hour forecast points:
    $$\text{Temp}(t) = T_0 + (T_1 - T_0) \times \frac{t - t_0}{3600}$$
  * This provides smooth, real-time temperature progression throughout the day with zero extra network requests or battery consumption.

### 2. Multi-Size Responsive RemoteViews (Android 12+)
* **Problem**: Resizing a widget on the home screen currently retains the same single layout until the next broadcast update occurs.
* **Proposed Solution**:
  * Implement Android 12's `RemoteViews(Map<SizeF, RemoteViews>)` constructor in `BaseWidgetUpdater`.
  * Define explicit responsive breakpoints:
    * **Compact (2x1 / 2x2)**: Time + Current temperature only.
    * **Standard (3x2 / 4x2)**: Time + Current conditions + High/Low + Location.
    * **Expanded (4x3 / 5x2)**: Time + Weather card + 5-day daily forecast row.
  * Allows instantaneous, fluid layout transitions directly within launcher animations.

### 3. Pre-Android 12 Dynamic Tile Sizing via Layout Variants
* **Problem**: Tile size customization (Small/Medium/Large/XL) is currently disabled on Android 10 and 11 because `setViewLayoutHeight` cannot be called remotely.
* **Proposed Solution**:
  * Generate layout XML variants for Pre-12 devices:
    * `widget_clock_block_small.xml`
    * `widget_clock_block_medium.xml`
    * `widget_clock_block_large.xml`
    * `widget_clock_block_xl.xml`
  * In `BaseWidgetUpdater`, select the layout resource dynamically when `Build.VERSION.SDK_INT < 31`, bringing full tile customization to older Android devices.

### 4. Timezone-Aware Clock for Remote / Pinned Locations
* **Problem**: If a user pins a specific city in a different time zone (e.g. London while currently located in Tokyo), `TextClock` defaults to device system time.
* **Proposed Solution**:
  * When updating a widget assigned to a fixed location, extract `location.timeZoneId`.
  * Pass the timezone to `TextClock` via `RemoteViews`:
    ```kotlin
    location.timeZoneId?.let { tz ->
        views.setString(R.id.clock_hour, "setTimeZone", tz)
        views.setString(R.id.clock_minute, "setTimeZone", tz)
        views.setString(R.id.ampm, "setTimeZone", tz)
    }
    ```

### 5. Tabular Digits & Monospace Alignment (`tnum`)
* **Problem**: Certain OEM system fonts alter glyph advance widths, causing minor horizontal digit shifts during minute transitions.
* **Proposed Solution**:
  * Add `android:fontFeatureSettings="tnum"` to all clock digit `TextView` and `TextClock` elements in layout XMLs.
  * Guarantees identical horizontal advance widths across all 0–9 numerals.

### 6. Bitmap Icon Memory Pooling & Caching
* **Problem**: Having multiple widgets placed simultaneously triggers redundant bitmap allocations and canvas rendering cycles for identical weather conditions.
* **Proposed Solution**:
  * Introduce an in-memory `LruCache<String, Bitmap>` keyed by `"${condition.name}_${iconStyle.name}_${maxDimensionPx}"`.
  * Re-use pre-rendered bitmaps across all widget instances, reducing garbage collection churn on low-memory devices.

---

## 4. Implementation Priority Matrix

| Feature | Target API | Priority | Complexity | Impact |
| :--- | :--- | :--- | :--- | :--- |
| **Hourly Forecast Temperature Interpolation** | All (API 26+) | **High** | Low | High (Accurate real-time temps without extra network calls) |
| **Timezone Synchronization for Pinned Cities** | All (API 26+) | **High** | Low | High (Correct time display for foreign locations) |
| **Tabular Numbers (`tnum`) Formatting** | All (API 26+) | **Medium** | Minimal | Medium (Prevents digit jitter across OEM fonts) |
| **Multi-Size Responsive Layouts** | Android 12+ (API 31+) | **Medium** | Medium | High (Seamless launcher widget resizing UX) |
| **Pre-12 Multi-Layout XML Sizing Variants** | Pre-12 (API < 31) | **Low** | Medium | Medium (Brings tile resizing to Android 10/11) |
| **Bitmap Icon LRU Cache** | All (API 26+) | **Low** | Low | Medium (Reduces GC pauses & allocation overhead) |
