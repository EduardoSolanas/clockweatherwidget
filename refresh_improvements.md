# Weather & Clock Widget Refresh Improvements

This document describes the current widget refresh and rendering architecture in Clock & Weather and proposes improvements in implementation order. It distinguishes observed weather from forecast-derived estimates, network refreshes from local redraws, and phase 1 (local-only) behaviour from the per-widget weather location planned for phase 2.

**Status audit: 11 September 2026.** DONE means the scoped code or recorded decision is present in the current checkout. PARTIAL means some acceptance work remains; OPEN means the proposed change is absent. Device verification is tracked separately. The current checkout contains the S1-S3, S5 and S6 implementations and their regression tests; S4 is the only finding still open.

**Toolchain and verification.** The toolchain is installed and Gradle runs: Microsoft OpenJDK 17.0.20.1, and the Android SDK at the `local.properties` path `C:\Users\eduar\AppData\Local\Android\Sdk` (cmdline-tools, platform-35, build-tools 35.0.0, platform-tools, licences accepted). `gradlew testDebugUnitTest lintDebug assembleDebug` compiles, runs and packages.

Latest run, covering S1, S2, S3, S5 and S6 together: **409 tests across 86 classes, 0 failures, 0 errors; lint 0 errors** (96 warnings, 2 hints, all pre-existing), and `assembleDebug` packages. Every DONE below is verified by that run unless its own text says otherwise. No device, emulator, live-provider, Doze, battery or deployment validation is claimed anywhere in this document.

See [section 8](#8-weather-sync-review--11-september-2026) for the implementation status and remaining S4/design and device-verification work. Earlier implementation history is in [Time, weather and location sync review](docs/TIME_WEATHER_SYNC_REVIEW.md).


## 1. Design Principles

- The displayed current temperature must not be presented as a live observation when it is forecast-derived.
- A widget redraw and a weather network refresh are different operations and should be scheduled independently.
- `appWidgetId` is the identity of a widget instance. Any per-widget location or configuration must be persisted against it.
- Responsive breakpoints must use dp dimensions rather than assumed launcher cell sizes because launcher grids vary by device.
- Battery optimizations should remove redundant work before adding caches or additional schedulers.
- Exact alarms should be reserved for functionality whose precision is genuinely user-critical.

## 2. Current Architecture

The current implementation has two related but distinct paths.

### Weather fetch path

```text
Periodic WorkManager / manual refresh / screen wake / relocation
                              |
                              v
                   WeatherUpdateWorker
                              |
                              v
        LocationRepository + WeatherRepositoryImpl
                              |
                              v
                     Room weather cache
                              |
                              v
             ClockWeatherApplication.refreshAllWidgets()
```

`WeatherUpdateWorker` refreshes every saved location, then redraws all active widgets from the cache. Automatic work is freshness-gated; user refreshes force a network request. Screen wake first attempts a cache-only redraw, then enqueues network-constrained freshness work. The runtime wake receiver is available only while the app process exists.

The weather page calls `RefreshWeatherUseCase` directly, observes the shared Room cache, and redraws widgets after a successful refresh. Foreground scope includes hourly forecasts, air quality and pollen. Background scope excludes hourly persistence and does not require an air-quality refresh; pollen depends on the widget setting. Open-Meteo can still return hours in its combined weather response and air quality alongside pollen. These different scopes require the consistency fixes in section 8.

### Local widget render path

```text
AppWidget update / resize / settings change / time or date change
                              |
                              v
                    BaseWidgetUpdater
                              |
               DataStore + Room cache read
                              |
                              v
                     WidgetDataBinder
                              |
                              v
             RemoteViews -> AppWidgetManager
```

`TextClock` provides host-driven minute changes without waking the application. Weather, date, layout, icon and preference changes require a new `RemoteViews` update.

### Important current constraints

- Every widget displays `getSavedLocations().firstOrNull()`. This is correct for phase 1 (section 4.1) and is the main thing phase 2 replaces.
- `WidgetConfigActivity` exists but is unwired - it returns `RESULT_OK` without saving anything, and no provider declares it via `android:configure`. `WidgetConfigScreen` and `WidgetConfigViewModel` exist alongside it. **Keep these**: they are the seed of the phase 2 configuration flow. They are currently unreachable, so leave them out of the phase 1 surface rather than deleting them.
- `WeatherDetailActivity` and `WeatherDetailViewModel` resolve the location themselves rather than from an intent extra. Correct for phase 1; phase 2 requires routing by extra.
- `PendingIntent` request codes already use `appWidgetId`, so intents are distinct between widget instances even though they all currently resolve the same location. This is already phase 2 ready.
- `WeatherData.locationZoneId()` intentionally returns the device time zone, and Open-Meteo requests forecast timestamps in the device time zone.
- `currentDisplayWeather()` returns the fetched `currentWeather` observation unchanged.
- All three widget providers declare `updatePeriodMillis="1800000"`, so the host's 30-minute callback and WorkManager's periodic job both trigger update paths. Only the WorkManager job performs a network refresh directly; the provider callback redraws and enqueues weather work only when the cached data is stale. `WidgetUpdatePeriodTest` enforces this floor deliberately; see the decision in section 4.3.
- Application startup and boot check for active widgets before scheduling periodic work and passive tracking; removing the final widget cancels periodic work and unregisters tracking. Settings interval changes also check for active widgets. **Remaining gap:** the weather page and settings permission-return paths can still register passive tracking without checking whether a widget exists.

## 3. Android Version Behaviour

| Dimension | Android 8-11 (API 26-30) | Android 12+ (API 31+) |
| :--- | :--- | :--- |
| **Clock rendering** | Four clipped `TextClock` views display one digit each. A 29dp frame clips a fixed-width 58dp two-digit clock, using left/right gravity to choose the visible digit. | Two spanning `TextClock` views use calculated letter spacing to position digits over the four tiles. |
| **Text sizing** | `RemoteViews.setTextViewTextSize()` is supported, but the clipped clock geometry is fixed and would no longer align if only the font size changed. | Font size, tile height and caption size are updated together at runtime. |
| **Layout sizing** | `RemoteViews.setViewLayoutHeight()` and width equivalents are unavailable, so geometry changes require XML layout variants. | Runtime layout dimensions are supported. |
| **Weather placement** | Uses the top-anchored weather icon and overlay variants to avoid launcher-specific vertical drift. | Uses the bottom-anchored icon with the same top-anchored text overlays. |
| **Weather icons** | Vector drawables are rendered in the application process to capped ARGB bitmaps, avoiding known OEM launcher vector-inflation failures. | Uses the same bitmap path for consistency. |
| **Resizing** | `onAppWidgetOptionsChanged()` rebuilds the current single layout after a resize. | The same callback works, and the platform can additionally select among responsive `RemoteViews(Map<SizeF, RemoteViews>)` layouts without waking the app for every size transition. |

References:

- [Provide flexible widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts)
- [RemoteViews API reference](https://developer.android.com/reference/android/widget/RemoteViews)

## 4. Foundations

Sections 4.1 and 4.3 record resolved decisions; 4.2 records implemented work. Device measurements and the permission-return tracking guard remain open in 4.3.

### 4.1 Resolved Decision: Local-only now, per-widget weather location later

Three distinct products were considered. They are separated here because conflating them is what made the earlier draft incoherent.

| | Clock follows | Weather follows | Status |
| :--- | :--- | :--- | :--- |
| **A. Local only** | Device | Device location | **Current phase** |
| **B. Multi-city weather** | Device | Per-widget selected city | **Planned, later phase** |
| **C. World clock** | Per-widget city | Per-widget city | **Not planned** |

**Decision:** ship A now, design toward B, keep C out of scope.

**Phase 1 (now).** Both time and weather reflect the device's current physical location and system timezone. This defers `appWidgetId -> locationId` mappings, the configuration flow and location-aware click routing without foreclosing them.

**Phase 2 (later).** Each widget instance selects its own weather location; the clock and date stay on device time. This is additive to phase 1 and requires:

- Persist `appWidgetId -> locationId`, and clear it in `onDeleted()`.
- Declare `WidgetConfigActivity` via `android:configure`, save the selection, and request the initial update before returning `RESULT_OK`.
- Resolve the assigned location in `BaseWidgetUpdater` instead of `getSavedLocations().firstOrNull()`, falling back to the primary location when unassigned.
- Pass `appWidgetId` and `locationId` to `WeatherDetailActivity` and honour them in the ViewModel.
- Define behaviour when an assigned location is deleted.

Because the clock stays device-local in phase 2, no timezone work is required for it. `WeatherData.locationZoneId()` returning the device zone remains correct.

**Phase 3 is explicitly not planned.** World-clock behaviour would additionally require the date, hourly-forecast selection and daily-forecast anchoring to move into the assigned city's zone, plus validating `Location.timezone` with `ZoneId.of()` and never passing the `"auto"` sentinel as a real zone ID. Do not implement partial versions of this - changing only `TextClock.setTimeZone()` produces a widget whose clock and date disagree. The `Location.timezone` column stays in the schema to keep the option open.

**Design constraint for phase 1 work:** do not add code that assumes one global location. Where it is free to do so, thread `appWidgetId` through rather than resolving the location from a singleton, so phase 2 is an extension rather than a rewrite.

### 4.2 DONE: Screen wake redraws from cache before refreshing

**Status: DONE in code.** [ScreenWakeReceiver](app/src/main/java/com/clockweather/app/receiver/ScreenWakeReceiver.kt) redraws cache with a 60s throttle on `ACTION_SCREEN_ON` and immediate redraw on `ACTION_USER_PRESENT`, then enqueues freshness work. This is opportunistic while the process is alive; no launcher, deployment or battery verification is claimed by this audit.

The redraw path uses [ClockWeatherApplication.refreshAllWidgets()](app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt), which shares one `WidgetRenderSnapshot` across every active widget and calls `updater.updateWidget(id, renderSnapshot)`. This batches reads; it does not establish atomic observation of the underlying tables (S4).

In `ScreenWakeReceiver`:

1. **`ACTION_USER_PRESENT` (Unlock):** Redraws immediately from cached data off the main thread.
2. **`ACTION_SCREEN_ON` (Ambient/Notification):** Throttled to minimum 60-second intervals to avoid waking on transient notifications.
3. **Network Enqueue:** Enqueues the existing freshness-gated refresh via WorkManager (`scheduleImmediateRefresh`, deduplicated via `KEEP`).

### 4.3 Resolved Decision: Retain the redraw watchdog (keep `1800000`)

**Decision:** We keep `updatePeriodMillis="1800000"` on all widget providers alongside WorkManager.

These are not two competing network schedulers. WorkManager is the primary periodic network-refresh mechanism. The provider callback is a **redraw and freshness watchdog**: it rebuilds the widget from cache, checks freshness via `BaseWidgetUpdater.scheduleRefreshIfStale()`, and enqueues refresh work only when the data is stale.

While Android explicitly recommends setting this to `0` when using WorkManager, real-world OEM battery killers (Samsung, Xiaomi, etc.) often aggressively defer WorkManager jobs. If a user keeps their screen on continuously (e.g., during navigation), WorkManager might stall, and screen-wake events won't fire, causing the widget to freeze.

**Implementation note:** `onUpdate` uses `BaseWidgetUpdater.createRenderSnapshot()` to batch reads and delegates freshness scheduling to `scheduleRefreshIfStale()`. Formal battery/CPU measurement under continuous screen-on remains an open verification item.

The 30-minute host callback is the OS-level safety net for that case. It is not free, and the document should not claim otherwise: every callback wakes the app, reads Room and DataStore, decodes and renders icon bitmaps, and parcels a full `RemoteViews` update for each widget instance. Android documents full widget updates as computationally expensive. Multiply that by every installed widget, twice an hour, indefinitely.

The decision is therefore to keep the watchdog but make it cheap, and to measure it rather than assume either way:

- Redraw cached content only; never force a network request from the callback itself.
- Suppress routine fallback-content flashes.
- Batch the shared Room/DataStore reads across all widget IDs in a single pass.
- Let the freshness check and the repository mutex suppress redundant requests.
- Measure wakeup cost and `RemoteViews` payload size before and after.
- Re-test on the OEM device that exhibited the WorkManager deferral.

`WidgetUpdatePeriodTest` remains correct and should not be altered.

**Status.** `1800000` is unchanged and the batching work is done. The measurement is **partially taken**:

- **Payload: measured.** See 5.1. It found and fixed a real 893,764-byte transaction; worst case is now 484,156 bytes single-view.
- **Rasterisation: count test implemented.** [WidgetWatchdogCostTest](app/src/test/java/com/clockweather/app/presentation/widget/common/WidgetWatchdogCostTest.kt) asserts 5 forecast-row icon renders for one row and 15 when binding three rows from the same data. These counts exclude the separate hero icon and do not time a full widget update. Shared snapshots remove duplicate Room/DataStore reads, but this test demonstrates repeated row bitmap work. Device allocation/render-time measurement for an icon cache (5.6) remains open.
- **On-device CPU and battery: not measured.** This needs the OEM device that exhibited the WorkManager deferral. Emulator figures would not be representative. Until this exists, no decision to remove the watchdog should be made.

**Regression, since fixed.** Hoisting the staleness check out of `updateWidget` gated it on `snapshot == null`, which made every caller passing a shared snapshot responsible for scheduling the refresh itself. `refreshAllWidgets` was updated for that; `BaseWidgetProvider.onUpdate` was not. The watchdog then redrew stale data and never enqueued a refresh - reopening the `d252045` freeze while `updatePeriodMillis` still read `1800000` and its guard test still passed. Fixed by extracting `BaseWidgetUpdater.shouldScheduleRefresh` (pure, takes an `Instant`) and `scheduleRefreshIfStale`, and calling the latter from every snapshot-passing site. `WidgetRefreshSchedulingTest` asserts on the call sites, not only the predicate, because the failure mode is a caller silently inheriting no check rather than two copies drifting apart.

**Evidence note.** The freeze is directly documented by commit `d252045`, which restored `1800000` and records the cause: "`updatePeriodMillis=0` meant Android never called `onUpdate` on its own, so the widget stayed frozen when the screen was continuously on." Commit `522ac4e` added the regression guard three minutes later. The specific "30-90 minute OEM deferral" figure in the test comment is documented rationale, not a captured measurement - treat it as a historical observation until logs or benchmarks exist.

Related implementation status:

- **DONE:** Application startup, boot and interval-setting changes gate periodic work on active widgets; `onEnabled` restores it and `onDisabled` cancels it after the final widget is removed. Startup/boot tracking registration and final-widget unregistration are also present.
- **PARTIAL:** Tracking is not yet limited to active widgets on every path. `WeatherDetailViewModel.refreshPermissions()` and `SettingsViewModel.refreshPermissionStatus()` register it after a grant without an active-widget check; `PassiveLocationManager.register()` checks permissions only.
- **DONE:** One cache/prefs snapshot (`WidgetRenderSnapshot`) is shared across all widget instances in a redraw batch instead of rereading Room and DataStore per widget ID.
- **DONE in code:** `BaseWidgetProvider` skips placeholder replacement for widgets with a recorded successful render. First placement, process recovery and flicker still need launcher verification.

These reduce the per-callback cost but do not substitute for measuring it.

Reference: [Advanced widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced)

## 5. Improvement Status

### 5.1 Android 12+ responsive `RemoteViews`

**Status: DONE for the forecast-icon cap and profiling tests; responsive breakpoints NOT ADOPTED.** The measurements below are recorded historical results and were not rerun in this audit.

The first attempt was withdrawn because `buildViews()` accepted `targetWidthDp` / `targetHeightDp` and never read either one. Every breakpoint produced a byte-identical `RemoteViews`, so the map multiplied the payload for no benefit. The test that shipped with it compared a hardcoded breakpoint list to itself and could not fail.

**What the profiling found.** `WidgetPayloadBudgetTest` parcels each widget's real `RemoteViews` and measures `Parcel.dataSize()`. Robolectric parcels bitmap pixel data faithfully - a 192x192 ARGB_8888 icon measures 147,684 bytes against 147,456 theoretical - so the numbers reflect what crosses the binder.

Two things had to be right for the measurement to mean anything, and both were wrong at first:

- **Measure every icon style.** Per-icon cost varies about 5x. GLASS_LAYERED (the default) renders 96x70 at ~27KB; CLAY_3D sources are 512x512 PNGs capped to 192x192 at ~147KB.
- **Anchor the fixture to wall-clock now.** `weatherToday()` filters forecast rows against the real date, so a fixed past date drops all five row icons and understates the payload roughly 5x.

With both corrected, the measurement surfaced a live bug unrelated to responsive layouts: **Extended and Forecast at CLAY_3D were shipping 893,764 bytes per update**, about 89% of the ~1MB launcher budget, in the shipped single-layout path. The five small forecast row icons were rasterising at the 192px hero cap.

**Fix.** `WidgetForecastIconMaxDimensionPx = 128` caps row icons separately from the hero icon. Worst case drops to 484,156 bytes, a 46% reduction, with no visible change at their display size.

**Responsive breakpoints: not adopted.** A breakpoint map parcels every mapped view into one transaction, so it only pays off if smaller sizes bind *less*. In these widgets the only content worth dropping is the five-day forecast row - the reason the widgets exist. Dropping it would have meant a widget named Forecast showing no forecast at its minimum resize height of 120dp. Keeping it means paying for duplicate identical views. Neither is worth a smoother resize animation, so `getResponsiveSizeBreakpoints()` returns `emptyList()` and each widget ships one layout.

This is a decision, not an omission. If breakpoints are ever revisited, `WidgetPayloadBudgetTest` must prove the **summed** transaction fits, and the smaller tier has to drop something the user genuinely does not need at that size.

Measured single-view payloads, worst-case CLAY_3D, against the ~1MB launcher budget:

| Provider | Before row-icon cap | After |
| :--- | ---: | ---: |
| Compact | 153,512 | 153,512 |
| Extended | 893,764 | 484,164 |
| Forecast | 893,716 | 484,116 |

### 5.2 Pre-12 clock size variants

**Status: OPEN, conditional on device evidence.**

**Recommendation: low priority; implement only if device testing shows material user value.**

The problem is geometry, not lack of remote text-size support. The 29dp clippers and 58dp two-digit `TextClock` widths are coupled to the 48dp font. Changing one value independently breaks digit extraction.

Because `widget_clock_block.xml` is included inside shared and provider root layouts, selecting a standalone clock-block resource at runtime is insufficient. Valid options are:

- Complete root layout variants for each provider and supported clock size, or
- Multiple complete clock blocks embedded in the root layout with one selected by visibility.

Prefer the approach with the smallest tested XML surface. Four sizes across three provider roots can otherwise create substantial duplication. Preview layouts must remain visually consistent with runtime layouts.

### 5.3 Temperature behaviour between network refreshes

**Status: PARTIAL.** `currentDisplayWeather()` already preserves fetched current conditions unchanged. A visible stale-data state remains open; forecast fallback/interpolation remains a product decision and is not implemented.

**Recommendation: do not describe interpolation as real-time accuracy.**

The fetched `currentWeather.temperature` is an observation snapshot. Hourly points are forecasts. Linear interpolation between two forecast points produces an estimate and may look precise without being more accurate.

Preferred policy:

1. Display the current observation while it is within the configured freshness window.
2. When it is stale and no refresh is possible, either continue displaying it with a stale indicator or fall back to the current-hour forecast with an explicit product decision.
3. Only add interpolation if the desired feature is explicitly an estimated temperature progression.

If interpolation is chosen:

- Name and test it as an estimate.
- Require adjacent forecast points around the reference instant.
- Reject missing, duplicate or non-hourly gaps rather than extrapolating.
- Use the device timezone consistently (section 4.1), including DST transitions.
- Clamp the fraction to `0.0..1.0`.
- Define which fields remain observed and which become forecast-derived; do not combine an interpolated temperature with a stale condition invisibly.
- Trigger cache-only redraws at a deliberately chosen cadence. This adds no network calls, but it is not zero battery cost.

### 5.4 Day/night icon transitions

**Status: DONE in code for local mapping at redraw.** [LocalDayNightDisplayMapper](app/src/main/java/com/clockweather/app/presentation/widget/common/LocalDayNightDisplayMapper.kt) selects day/night variants using the reference time and today's sunrise/sunset, with a 06:00-20:00 fallback when solar times are unavailable or equal. It handles paired clear/partly-cloudy conditions and preserves unpaired weather phenomena.

[WidgetDataBinder](app/src/main/java/com/clockweather/app/presentation/widget/common/WidgetDataBinder.kt) applies the mapping during cached rendering. [LocalDayNightDisplayMapperTest](app/src/test/java/com/clockweather/app/presentation/widget/common/LocalDayNightDisplayMapperTest.kt) covers the pure mapping; it was not rerun in this audit. The change appears on the next redraw, not at a guaranteed exact sunrise/sunset deadline. Device and timezone edge-case validation remains separate. No new alarm is required by this completed scope.

Reference: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)

### 5.5 Tabular digits

**Status: DONE in XML.** Runtime and preview clock text already include `android:fontFeatureSettings="tnum"` alongside the monospace family. This is a font hint, not a guarantee of identical rendering on all OEM devices.

The hint is present in [widget_clock_block.xml](app/src/main/res/layout/widget_clock_block.xml) and [widget_clock_block_preview.xml](app/src/main/res/layout/widget_clock_block_preview.xml). Its effect still depends on the selected font's OpenType support.

Remaining verification: inspect devices or emulators that previously demonstrated digit jitter. Do not add the hint again as a pending code task.

### 5.6 Bitmap icon caching

**Status: OPEN.** The rasterisation-count test exists, but no bitmap LRU cache is implemented. Device allocation/render-time evidence remains a prerequisite.

**Recommendation: profile before implementing.**

An in-process cache can reduce repeated drawable inflation, bitmap allocation and canvas drawing. It does not reduce the bitmap data attached to each `RemoteViews` transaction, and it retains memory for longer.

If profiling shows meaningful allocation churn:

- Use a byte-bounded `LruCache`.
- Key by drawable resource ID, rendered width, rendered height, density and any resource configuration that changes the pixels.
- Do not key only by condition/style because the renderer receives a concrete drawable resource and dimensions.
- Do not recycle cached bitmaps that may still be referenced by an update being parcelled.
- Clear or naturally replace entries after relevant configuration changes.
- Measure allocation count, render time, process memory and RemoteViews transaction size before and after.

### 5.7 Jetpack Glance investigation

**Status: OPEN research option; no migration or parity spike is implemented.**

**Recommendation: research spike only, not an assumed migration.**

Glance offers declarative widget construction and responsive size modes, but it still produces `RemoteViews`, is not interoperable with ordinary Compose UI elements, and remains subject to widget limitations. The current implementation relies on host-driven `TextClock`, precise clipping/letter-spacing geometry and bitmap fallbacks for OEM launchers.

A spike must prove parity for:

- Minute-by-minute host-driven clock updates without waking the app.
- 12/24-hour formats and AM/PM in the device timezone.
- Pre-12 clipped digit behaviour or an equivalent design.
- All clock themes and text/tile sizes.
- Current and forecast widget layouts across supported sizes.
- OEM-safe weather icon rendering and acceptable transaction sizes.
- Existing click, refresh, configuration and failure fallbacks.
- A per-widget configuration activity and per-instance state, since phase 2 of section 4.1 depends on it.

Do not migrate production providers until the spike demonstrates a clear reduction in complexity without losing reliability.

Reference: [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)

## 6. Revised Priority Matrix

| Work item | Priority | Complexity | Expected value | Status / Dependency |
| :--- | :--- | :--- | :--- | :--- |
| Screen wake: cached redraw before freshness-gated refresh | **P0** | Low | Cache redraw while offline | **DONE in code; process must be alive** |
| Gate background work on active-widget existence | **P1** | Low-Medium | Less background work with no widget installed | **PARTIAL: periodic/lifecycle guards DONE; permission-return tracking guard OPEN** |
| Batch cache/prefs reads across a redraw | **P1** | Low | Fewer redundant Room/DataStore reads per update | **DONE; atomic reads remain S4** |
| Watchdog freshness & staleness check unification | **P1** | Low | Ensures 30-min callback and batch redraws schedule refresh if stale | **DONE for shared call sites and configured coverage** |
| Preserve populated widgets during routine callbacks | **P1** | Low | Avoids placeholder flashes | **DONE in code; launcher checks OPEN** |
| Local day/night display mapping | **P2** | Low-Medium | More timely visual state | **DONE in code; device checks separate** |
| Tabular-number hint (`tnum`) | **P3** | Minimal | Small OEM typography defence | **DONE in XML; device checks separate** |
| Android 12+ responsive layouts | **Not adopted** | Medium | Only pays off by dropping the forecast row; not worth it | See 5.1 |
| Cap forecast row icons (128px) | **DONE** | Low | Historically measured update reduction, about 894KB -> 484KB | Code present; figures not rerun |
| Payload + rasterisation measurement | **DONE** | Low | Budget/count tests exist | Historical measurements; not device battery evidence |
| S1: invalidate old-city hourly cache | **P1** | Low | Prevents mixed-city forecasts | **DONE, verified, section 8** |
| S5: include optional-section freshness | **P1** | Low | Refreshes stale/missing AQ and pollen on resume | **DONE, verified, section 8** |
| S3: measure movement from weather coordinates | **P2** | Low | Detects accumulated small moves | **DONE, verified, section 8** |
| S6: avoid duplicate startup refreshes | **P2** | Low-Medium | Avoids a forced paid download on every screen open | **DONE, verified; device acceptance separate** |
| S2: preserve newest queued location fix | **P2** | Medium-High | Prevents delayed work reverting location | **DONE, verified, section 8** |
| S4: observe one coherent Room snapshot | **P3** | Medium | Prevents mixed refresh generations | **OPEN, section 8 — downgraded from P2, re-evaluate after S1** |
| Watchdog CPU/battery on-device | **P1 - open** | Low-Medium | Needs the OEM device that showed the deferral | Hardware |
| Pre-12 layout variants | **P2** | Medium-High | Older-device size customization | Device evidence |
| Forecast-derived temperature fallback/interpolation | **P2/P3** | Medium | Cosmetic progression with accuracy trade-off | Screen-wake redraw and product policy |
| Bitmap LRU cache | **P3** | Low-Medium | Possible allocation reduction | Profiling evidence |
| Glance migration | **Research** | High | Unknown until parity spike | Stable feature requirements |
| Persist `appWidgetId -> locationId` | **Deferred - 4.1 phase 2** | Medium | Foundation for multi-city | Section 4.1 phase 2 |
| Wire `WidgetConfigActivity` (`android:configure`, save, initial update) | **Deferred - 4.1 phase 2** | Medium | Lets each widget pick its city | Persistence |
| Route detail screen by `appWidgetId` / `locationId` | **Deferred - 4.1 phase 2** | Medium | Completes multi-city click behaviour | Persistence |
| World-clock timezone pipeline | **Not planned** | High | Out of scope; see 4.1 phase 3 | - |

## 7. Verification Requirements

Every behavior change should follow red-green-refactor with real objects and run the full test suite. The checks below are proposed acceptance requirements, not claims that they were all performed. Existing tests are identified where relevant; their presence alone does not establish that the acceptance requirement is satisfied. Documentation-only updates require prose, path/link and diff checks instead of application tests. In addition:

- Add contract tests for no-widget behaviour: no periodic work and no passive location tracking while zero widgets are installed.
- Test that a screen wake with no network still redraws cached content, and that a wake with fresh data issues no request.
- Test date and forecast-day rendering around midnight and across DST gaps/overlaps in the device timezone.
- Test that the `"auto"` timezone sentinel is never passed to `ZoneId.of()`.
- *(Phase 2)* Persistence and deletion tests for every `appWidgetId -> locationId` mapping, including `onDeleted()` cleanup.
- *(Phase 2)* Two widget IDs assigned to different locations render different weather and route to different detail targets.
- *(Phase 2)* An unassigned widget falls back to the primary location; a widget whose assigned location was deleted degrades predictably.
- *(Responsive - only if revisited; currently not adopted)* Assert that two breakpoints produce **different** rendered views, and that the total parcel size stays under the binder budget at every icon style. Asserting the breakpoint list matches a hardcoded list is what let the first attempt ship broken - such a test cannot fail. Any new content tier must re-measure the summed payload before it lands.
- Anchor any fixture that touches forecast rows to wall-clock now. `weatherToday()` filters rows against the real date, so a fixed past date silently drops all five row icons and understates payload measurements roughly 5x.
- Test cache-only redraw while offline.
- Test day/night mapping immediately before and after sunrise/sunset.
- Test pre-12 layouts at API 26 and API 30, plus API 31+ for the responsive path, with Robolectric coverage and real launcher hosts where clipping behaviour matters. `minSdk` is 26, so API 30 alone is not sufficient pre-12 coverage.
- Measure bitmap allocation and RemoteViews payloads before accepting caching as an optimization.
- Visually verify Android 8 (API 26), Android 10/11, Android 12+, at least one high-density device, and an OEM launcher previously affected by vector inflation.

## 8. Weather Sync Review — 11 September 2026

Six findings were raised. **S1-S3 and S5-S6 are now implemented; S4 remains OPEN by design.** They are source-derived failure scenarios, not device reproductions. The current code and tests were reviewed and run together; the toolchain state and the resulting counts are recorded at the top of this document. Preserve host-driven `TextClock`, useful offline cache and the background request savings while resolving the remaining evidence gaps.

Two priority corrections came out of that re-check and are carried into [section 6](#6-revised-priority-matrix): **S4 drops to P3** and **S6 is under-rated at P2**. Both are argued in place below.

### S1 — P1: Invalidate hourly forecasts after relocation

- [x] **DONE, verified:** Prevent a new city's current/daily weather from retaining the previous city's hourly graph.

**Evidence:** [WeatherRepositoryImpl.persistWeatherData](app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt) replaces hourly rows only when `scope.includeHourly` is true. [WeatherUpdateWorker](app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt) uses background scope even for a forced relocation. Current and daily data move to city B while city A's hours retain the same `locationId`. The foreground freshness predicate checks hourly time coverage, not ownership, so a warm resume shortly after the background refresh can accept this mixed cache.

**Root cause, corrected.** The original proposed fix — "invalidate hours whose location identity no longer matches" — is not implementable as written. `WeatherRefreshLocationResolver.resolve` returns `detectedLocation.copy(id = savedLocation.id)`, so the row id is deliberately *stable* across a move. `locationId` is therefore structurally incapable of expressing ownership and there is no mismatch to detect.

The sharper defect is that the worker already believes it handles this. On a significant move it escalates to `WeatherRefreshMode.FORCE`, under the comment *"Cached weather is keyed by the location row, so right after a move it still holds the city the user left. Refetch however recent it looks."* But it then calls `forceRefreshWeatherData(refreshLocation, forecastDays)` with no scope argument, which falls through to `backgroundScope()`, where `includeHourly` is false — so the `deleteHourlyForecasts` branch never runs. **The mitigation is defeated by a defaulted parameter.**

**Implemented fix:** `persistWeatherData` now receives the pre-fetch `CurrentWeatherEntity` and derives ownership from the coordinates the previous fetch recorded on the weather row — the same provenance `cacheDescribes` already uses for optional sections. When the scope bought no hours but the committed weather is `SIGNIFICANT_MOVE_METERS` or more from those coordinates, the stale hours are dropped. Deletion requires *proven* movement: rows migrated from before the coordinate columns existed hold null and are left alone, so pre-migration caches are not wiped on every background refresh.

**Known limitation of that choice.** A weather row migrated from before the coordinate columns existed cannot prove where it came from, so its hourly rows are never invalidated by a background relocation and the old city's graph survives until the first foreground fetch replaces the hours wholesale. This is deliberate: the alternative — treating unknown provenance as movement — would wipe the hourly cache of every pre-migration install on its next background refresh, for users who had not travelled at all. The window closes the first time the app is opened, and it closes permanently for that row, because any fetch from then on records coordinates.

**Acceptance coverage:** `RelocationCacheReuseTest.background relocation invalidates hourly rows owned by the previous city` seeds London coordinates, inserts an hourly row, forces a background-scope refresh at Brighton and asserts zero hourly rows remain. *(Correction: an earlier revision of this document claimed `RelocationCacheReuseTest` covered only optional-section cache eligibility. That was true at `017b11c` but the test above was already present uncommitted in the working tree.)* Passing.

### S2 — P2: Preserve the newest location fix across queued work

- [x] **DONE, verified:** Preserve and process the newest passive fix without cancelling work already in flight.

**Evidence:** [LocationUpdatesReceiver](app/src/main/java/com/clockweather/app/receiver/LocationUpdatesReceiver.kt) passes coordinates into [WeatherUpdateScheduler.scheduleUserRefresh](app/src/main/java/com/clockweather/app/worker/WeatherUpdateScheduler.kt). Its unique-work policy is `KEEP`, so a newer fix C is discarded while B is queued or running. The worker prefers B's input coordinates over a fresh location lookup and has no original fix timestamp to validate. After network delay or retry, it can publish B although the device has moved on.

**Sequencing note: real, but the riskiest of the six — do it last.** `ExistingWorkPolicy.KEEP` does discard fix C, but switching to `REPLACE` makes things worse, not better: continuous movement would cancel in-flight work repeatedly and no refresh would ever complete. This is the one finding where the obvious reading of "fix the policy" is a regression. The design below is correct precisely because it keeps `KEEP` and moves the fix outside the WorkManager request; the untracked `LatestLocationFixStoreTest` in the working tree already sketches it against a real Preferences DataStore.

**Implemented fix:** `LatestLocationFixStore` persists the newest accepted fix by observation time. The worker reads it when it starts and clears it only if the same timestamp is still stored after the fetch, so a newer fix cannot be erased by an older run. Passive relocation uses `APPEND_OR_REPLACE` to leave a follow-up worker behind an in-flight run; explicit user refreshes retain `KEEP`.

**Acceptance coverage:** `LatestLocationFixStoreTest` uses a real Preferences DataStore for ordering, the in-flight clear race, and the age bound — a fix one millisecond past `MAX_QUEUED_FIX_AGE_MS` is rejected, and so is one dated in the future. `LocationUpdatesReceiverTest` covers the relocation scheduling path and the guard around recording the fix: a DataStore failure must still enqueue `scheduleRelocationRefresh`, because the request carries no coordinates of its own and an unguarded throw would drop the relocation silently until the next periodic run; and cancellation must still propagate, because the surrounding `withTimeout` enforces the broadcast budget by cancelling — a guard written as `runCatching` would catch that and defeat it. A full WorkManager execution with B and C remains a useful device/integration check.

### S3 — P2: Measure accumulated movement from the weather snapshot

- [x] **DONE, verified:** Trigger a refresh when requested coordinates move 5 km or more from the last successful weather snapshot.

**Evidence:** [WeatherUpdateWorker.doWork](app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt) compares the detected fix with `savedLocation`, then saves the detected location even when `ensureFreshWeatherData` skips a fetch. Successive worker/wake fixes less than 5 km apart advance that row without advancing the weather snapshot. With a long refresh interval, their cumulative displacement can be much greater than 5 km while the old weather still passes its age check. The cache-ownership guard in `refreshAndPersist` runs only after a fetch has already been selected.

**Magnitude, corrected.** The drift is bounded by the refresh interval, not open-ended. `ENSURE_FRESH` passes `maxAgeMinutes = refreshIntervalMinutes`, so the periodic run itself almost always finds the weather older than the interval and refetches, re-anchoring the snapshot. The skip-then-`saveLocation` sequence only accumulates via *extra* triggers between periodic runs — screen wake, location updates, boot. Real, and worse with a long configured interval, but the ceiling is interval × travel speed rather than unbounded.

**Implemented fix:** `ensureFreshWeatherData` compares requested coordinates with the weather row's stored coordinates before accepting a fresh cache. Unknown coordinates are treated as unowned, and the existing resolver continues to preserve reuse below the 5 km threshold.

**Why this is the best remaining item:** `WeatherRefreshLocationResolver.cacheDescribes` already computes exactly this comparison and is already imported by the repository — it is simply never consulted in the freshness decision. Lowest cost of the four remaining, which is why the matrix now lists it ahead of S6 and S2.

**Acceptance coverage:** `RelocationCacheReuseTest` seeds a fresh London snapshot and verifies a Brighton request still fetches. The smaller-movement reuse case remains covered by the existing resolver tests.

### S4 — P2: Observe current, hourly and daily data as one snapshot

- [ ] **OPEN:** Prevent an observation from combining different refresh generations.

**Evidence:** [WeatherRepositoryImpl.getWeatherData](app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt) combines four independent DAO flows. `persistWeatherData` writes within a transaction, but each flow can emit its new value at a different time. An active page collector can temporarily receive new current weather with old forecasts; independent initial queries can also straddle a commit. Sharing one `WidgetRenderSnapshot` across widgets reduces reads but does not make those underlying reads atomic.

**Priority corrected: P2 → P3, and sequence it last.** The `combine` over four DAO flows is exactly as described, but writes go through `database.withTransaction` and Room's invalidation tracker fires only after the transaction commits, so all four flows re-query post-commit. The skew window is milliseconds — one frame on a graph. More importantly, most of the harm attributed to S4 was actually **S1**: a *persistent* mixed generation rather than a transient one. With S1 fixed, re-evaluate whether a medium-effort change to the main read path — which the widgets also use — still earns its place on source-derived evidence alone. Do not start this one before there is a visible artifact to point at.

**Proposed fix:** Observe an aggregate snapshot read under one Room transaction, retaining the existing transactional write boundary.

**Proposed acceptance:** Collect repository emissions while committing distinguishable A/B snapshots through real Room. Every non-null emission must contain one complete generation. `WeatherRepositorySnapshotTest` currently reads once after seeding and does not test emissions during writes.

### S5 — P2: Check freshness of requested air quality and pollen

- [x] **DONE, verified:** Refresh stale or missing optional sections when the weather page requests them.

**Evidence:** [WeatherRepositoryImpl.ensureFreshWeatherData](app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt) returns early based on core weather and forecast coverage, consulting only `includeHourly` from the scope. It never includes `includeAirQuality` or `includePollen` in that decision. Their independent timestamps and provider reuse checks are **DONE**, but those checks are not reached when the repository skips fetching. A warm resume after a background refresh can therefore keep expired AQ or missing pollen behind fresh current weather and sufficient cached hours.

**Implemented fix:** `ensureFreshWeatherData` now also requires `optionalSectionsFresh(...)` before returning early. Sections the scope does not request are not consulted, so background work stays exactly as cheap as it was and cannot be held stale by data no widget displays.

The loop hazard is handled by changing what the timestamp *means*. `WeatherProviderType` carries no capability flags, so "air quality is absent" cannot be distinguished from "this provider does not publish air quality here" by looking at the data. Judging freshness on the presence of data would make an unavailable section read as permanently missing and enqueue a refresh on every check, forever. Instead `aqLastUpdated` and `pollenLastUpdated` are now recorded as **when the section was last resolved, not when data last arrived**: a refresh that asked and got nothing back, including a provider response that only echoes the stale cache, stamps the attempt, which is itself an answer ("unavailable here") and holds for one TTL. A section nobody asked for keeps whatever timestamp it had, so an unrelated refresh cannot make it look freshly checked. The new `isOptionalSectionFresh` predicate reads that marker alone.

No migration is required — the `aqLastUpdated` and `pollenLastUpdated` columns already exist from finding 1.

**Cost trade-off, stated plainly.** This fix *increases* foreground request volume, deliberately. Previously a fresh current-weather reading (10-15 min TTL) short-circuited the whole check; now an air-quality reading older than its 60-minute TTL will trigger a fetch even when current weather is fresh. The ceiling is roughly one extra fetch per hour of active app use, and the provider still reuses whichever sections are individually fresh, so a triggered refresh does not re-buy everything. Background scope is untouched and the savings from `c284140` and `794f96d` are preserved in full. This is correctness bought at a known price, and it should be weighed against S6, which removes a forced download on *every* screen open.

**Acceptance coverage:** `OptionalSectionFreshnessTest` (new, real Room + `MockWebServer`) seeds a cache the pre-S5 predicate called entirely fresh — current conditions from this minute, 25 hours from the current hour, seven days of daily coverage — so the request count observes the optional sections and nothing else. Six cases: expired air quality refreshes; air quality never recorded refreshes on first foreground request; fresh sections make no request; background scope is not held stale by sections no widget displays; an empty section is marked as asked; and a stale cached section does not retain its old observation timestamp after an empty response. Passing.

**Known gap in that coverage.** The end-to-end "bought once per TTL, not once per check" case is *not* covered. The shared Open-Meteo fixture returns `"hourly": null, "daily": null`, so the first fetch wipes both tables and any second `ensureFreshWeatherData` call re-fetches on missing hourly coverage — failing for a reason that has nothing to do with S5. The loop guard is therefore covered at the mechanism level only (the marker is stamped when the provider returns nothing). Closing this properly needs a fuller fixture carrying real hourly and daily arrays, which would also let `RelocationCacheReuseTest` assert what replaces the invalidated hours rather than only that they are gone.

### S6 — P2: Avoid a second full refresh on screen creation

- [x] **DONE, verified:** Treat already-granted location permission differently from a newly granted permission.

**Evidence:** [WeatherDetailScreen](app/src/main/java/com/clockweather/app/presentation/detail/screen/WeatherDetailScreen.kt) runs `LaunchedEffect(allPermissionsGranted)` on initial composition and calls `viewModel.refresh()` when permission was already granted. [WeatherDetailViewModel.loadWeather](app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailViewModel.kt) independently launches `ensureFreshWeatherAndWidgets`. The manual request forces a download, and the repository mutex serializes rather than merges it. Stale-cache startup can download twice; reopening a newly created page can force a request despite fresh cache. The first-resume gate does not suppress this Compose effect.

**Under-rated at P2.** `refresh()` ends in `forceRefreshWeatherAndWidgets`, unconditionally, so every screen open forces a paid download regardless of how fresh the cache is — `ManualRefreshGate` is a cooldown on repeated manual refreshes and does not dedupe against `loadWeather`'s `ensureFresh`. This branch spent `c284140` and `794f96d` cutting request volume on a per-request-billed API; a guaranteed forced fetch per screen open partly undoes that work. The cost argument, not the duplicate-download argument, is what should decide this one's position.

**Implemented fix:** `LocationPermissionRefreshGate` stays silent for an already-granted permission on first composition, while a denial-to-grant transition still calls `viewModel.refresh()`. Explicit pull-to-refresh gestures are unchanged.

**Acceptance coverage:** `LocationPermissionRefreshGateTest` covers the initial-granted and denial-to-grant state transitions. Full Compose startup with shared Room data and real permission UI still needs device/integration verification.
