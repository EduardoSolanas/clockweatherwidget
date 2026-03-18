# Clock Weather Widget — Master Plan

> Generated: 2026-03-18  
> Covers all open work across battery, clock accuracy, weather graphs, animated icons, and code quality.

---

## 🔴 High Priority

### Battery & Receivers
- [x] **B1** — `ScreenStateReceiver`: pass resolved `isHighPrecision` on screen-on/dreaming-stopped instead of hardcoded `true` (`ScreenStateReceiver.kt` lines 33, 55)
- [x] **B2** — `SettingsViewModel.setHighPrecisionEnabled()`: pass `enabled` into `scheduleNextTick()` instead of defaulting to `true` (`SettingsViewModel.kt` line 221)

### Clock Accuracy
- [x] **C1** — Store `h1, h2, m1, m2` in `WidgetClockStateStore` and use them for the incremental diff in `bindClockViews()` instead of computing `prevMinute = minute - 1` arithmetically (`WidgetDataBinder.kt` lines 92–105)

### Weather Graph
- [x] **G1** — Pre-convert all temperatures to the selected unit before computing `minTemp`/`maxTemp` and Y positions — fixes dot/label mismatch in Fahrenheit mode (`HourlyWeatherGraph.kt` lines 81–88, 97) ⭐ *Do first*

---

## 🟡 Medium Priority

### Weather Graph
- [x] **G2** — Remove the `Row` overlay; move all labels (temperature, time, precipitation %) into the `Canvas` block via `TextMeasurer` + `drawText()` to fix overdraw and alignment (`HourlyWeatherGraph.kt` lines 78–210) — *depends on G1*
- [x] **G3** — Add Y-axis grid lines and labels: reserve 32dp left padding, compute 3–4 "nice" tick values, draw dashed horizontal lines with temp labels — *depends on G1, G2*
- [x] **G4** — Add gradient fill under the temperature curve using `Brush.verticalGradient` drawn before the stroke path — *depends on G1*
### Battery & Performance
- [x] **B3** — Wire `KEY_UPDATE_INTERVAL` pref into `WeatherUpdateScheduler`: add `schedule(context, intervalMinutes)` overload, call it from `SettingsViewModel.setUpdateInterval()` and `ClockWeatherApplication.onCreate()` (`WeatherUpdateScheduler.kt` line 20)
- [x] **B4** — Deduplicate concurrent weather refreshes with a `Mutex` per location (or `@Volatile` timestamp guard: skip if last network call < 2 min ago) (`WeatherRepositoryImpl.kt`)
- [x] **B5** — For `useSimple` path in `updateClockOnly()`, push only the 4 digit views + AM/PM partial update instead of rebuilding the full layout (`BaseWidgetUpdater.kt` lines 264–267)

### Clock Accuracy
- [x] **Task 5** — Widen incremental update window from exactly `gap == 1` to `gap in 1..3` minutes in `WidgetClockUpdateModeResolver`; update unit tests (`WidgetClockUpdateMode.kt`, `BaseWidgetUpdater.kt`)

### Infrastructure
- [x] **Task 4** — Create `WidgetPrefsCache` singleton: Flow collector keeps an in-memory `Preferences` snapshot; replace per-tick `DataStore.data.first()` calls in `updateClockOnly()` with cache reads (`WidgetPrefsCache.kt`, `ClockWeatherApplication.kt`, `BaseWidgetUpdater.kt`)

---

## 🟢 Low Priority

### Weather Graph
- [x] **G5** — Day/night background shading: draw subtle dark overlay for `!forecast.isDay` entries before other canvas content — *depends on G2*
- [x] **G6** — Replace flat `drawRect` precipitation bars with `drawRoundRect` + `Brush.verticalGradient` — *depends on G2*
- [x] **G7** — Edge case handling: artificial ±2° padding when all temps equal, placeholder for < 2 entries, filter sentinel values — *depends on G1*
- [x] **G8** — Auto-scroll `LaunchedEffect` to current hour index on open (`HourlyWeatherGraph.kt`)
- [x] **G9** — Accessibility: set `contentDescription` on the graph Card with high/low temp and times (`HourlyWeatherGraph.kt`)

### Battery & Receivers
- [x] **B6** — Wrap all `CoroutineScope(Dispatchers.Default)` in BroadcastReceivers with `supervisorScope { withTimeout(10_000) { … } }` + `finally { pendingResult.finish() }` (`ClockAlarmReceiver.kt`, `BootCompletedReceiver.kt`, `TimeChangedReceiver.kt`, `ScreenStateReceiver.kt`)
- [x] **Task 3** — Battery-level awareness in `scheduleNextTick()`: >15% → normal, 6–15% → force non-wakeup inexact, ≤5% → cancel alarm entirely (`ClockAlarmReceiver.kt`)
- [x] **Task 7** — AOD/dreaming handling: listen for `ACTION_DREAMING_STARTED` / `ACTION_DREAMING_STOPPED` in `ScreenStateReceiver`; check `PowerManager.isInteractive()` as fallback

### Code Quality
- [ ] **Q1** — Gate `debugIndex` condition-cycling in `CurrentWeatherCard.kt` behind `if (BuildConfig.DEBUG)` (lines 124, 199)
- [ ] **Q2** — Expand unit tests: `WidgetPrefsCache`, `TemperatureFormatter.convert()` edge cases, `ClockAlarmReceiver` battery tier logic

---

## 🎨 Weather Animated Icons (`WeatherAnimatedIcon.kt`)

All conditions have *some* animation, but several share the same composable without visual distinction. Tasks below add missing unique behaviours.

### New Composables (missing visual distinction)
- [ ] **S1** — `FreezingRainBackground` — blue-tinted heavy rain with translucent hexagonal ice-crystal particles mixed in; wire to `FREEZING_RAIN_LIGHT` / `FREEZING_RAIN_HEAVY` (currently uses plain `RainBackground`)
- [ ] **S2** — `FreezingDrizzleBackground` — frost-tinted mist with small hexagonal ice fragments drifting down; wire to `FREEZING_DRIZZLE_LIGHT` / `FREEZING_DRIZZLE_HEAVY` (currently uses plain `DrizzleBackground`)
- [ ] **S3** — `SnowGrainsBackground` — dense small ice pellets (solid circles, no bokeh blur, harder vertical fall, bounce-stop at bottom); wire to `SNOW_GRAINS` (currently uses fluffy `SnowBackground`)
- [ ] **S4** — `HailThunderstormBackground` — extend `ThunderstormBackground` with a `heavy: Boolean` param; when `true`, add falling hail-stone particles (grey/white rounded rects bouncing at the bottom); wire to `THUNDERSTORM_SLIGHT_HAIL` / `THUNDERSTORM_HEAVY_HAIL`

### Existing Composable Improvements
- [ ] **S5** — `PartlyCloudyBackground` (isDay=true): add rotating sun rays behind the cloud (reuse ray logic from `SunBackground`) so the daytime variant feels warmer and distinct from the night version
- [ ] **S6** — `DrizzleBackground`: replace sparse mist circles with fine diagonal hairline streaks + micro-droplet splashes to visually distinguish from fog and be more recognisable as drizzle
- [ ] **S7** — `FogBackground`: add horizontal parallax drift — far layers (`i < 2`) scroll at 0.3× speed, near layers at 1.0× — plus a subtle vertical sine oscillation per layer to make the fog feel alive
- [ ] **S8** — `RainBackground` / `RainShowerBackground`: add a shower burst cycle for `RAIN_SHOWER_*` variants — intensity pulses on a ~8 s cycle (particle count or speed oscillates) to convey intermittent bursts vs. steady rain; either add `isShower: Boolean` param or split into a dedicated `RainShowerBackground`

### Performance
- [ ] **S9** — Consolidate frame-time source: each animated composable runs its own `rememberTime()` coroutine via `withFrameMillis`. Replace with a single `val frameTime by produceState(0L) { while(true) { withFrameMillis { value = it } } }` hoisted at the `WeatherAnimatedIcon` entry point and passed down, eliminating N parallel frame coroutines

---

## ⚪ Optional / Deferred

- [ ] **G11** — Wind speed overlay: secondary dashed line with right-side Y-axis labels + "Show wind" toggle chip — *depends on G2, G3*
- [ ] **Task 6** — Replace 4-digit TextViews with `TextClock` for `usesSimpleClockDigits = true` variants to eliminate alarm overhead entirely

---

## Dependency Map

```
G1 ──► G2 ──► G3
  │     │ ──► G5
  │     └──► G6
  └──► G4
  └──► G7

C1 ──► (resolves incremental diff accuracy for Task 5 gap widening)
Task 4 ──► B5 (cache ready before optimising partial updates)
G2, G3 ──► G11
```



