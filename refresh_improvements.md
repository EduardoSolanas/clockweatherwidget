# Weather & Clock Widget Refresh Improvements

This document describes the current widget refresh and rendering architecture in Clock & Weather and proposes improvements in implementation order. It distinguishes observed weather from forecast-derived estimates, network refreshes from local redraws, and phase 1 (local-only) behaviour from the per-widget weather location planned for phase 2.

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

`WeatherUpdateWorker` refreshes every saved location, then redraws all active widgets from the cache. Automatic work is freshness-gated; user refreshes force a network request. Screen wake currently enqueues network-constrained work rather than performing an immediate cache-only redraw.

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
- Application startup schedules periodic weather work and passive location tracking even when no widget is active.

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

Sections 4.1 and 4.3 record resolved decisions. Section 4.2 is the single unconditional next piece of work: a small wiring fix that needs no further decision and blocks nothing else. Note that section 5.1 (responsive layouts) does not depend on anything in this section and can proceed in parallel.

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

### 4.2 Work: make screen wake redraw from cache before refreshing

**This is the one item to build next.** It is a wiring fix, not a new architectural layer.

The redraw path already exists. `ClockWeatherApplication.refreshAllWidgets()` loops `updater.updateWidget(id)` over every active widget, reading from the Room cache and DataStore. The timezone/date-change and package-replacement receivers already use it.

The gap is `ScreenWakeReceiver`. On `ACTION_SCREEN_ON` / `ACTION_USER_PRESENT` it calls `WeatherUpdateScheduler.scheduleImmediateRefresh()` and nothing else. That work request carries a `NetworkType.CONNECTED` constraint, so on an offline or flaky wake the widget receives **no update at all** - not even a redraw of the data already sitting in cache. The user looks at a widget still showing whatever it rendered hours ago.

Change the receiver to do both, in order:

1. Redraw immediately from cached data.
2. Then enqueue the existing freshness-gated refresh.

Notes for implementation:

- Keep the redraw off the main thread; `refreshAllWidgets()` is already `suspend`.
- `BaseWidgetUpdater.updateWidget()` is not purely local on first run: when no locations are saved it calls `getCurrentLocation()` behind a 6s timeout and persists a fallback. That path is acceptable on wake but must not be treated as a guaranteed-offline operation.
- Screen wake fires often. Rely on the existing freshness gate and the repository mutex rather than adding a second dedupe mechanism.
- Do not render fallback or placeholder content on a routine wake redraw; a flash of empty state is worse than briefly stale data.

Naming the two operations clearly keeps later triggers honest: **refresh weather** (network, freshness-gated, persists, then redraws) versus **redraw widgets** (renders from cache and preferences, issues no network request directly). The redraw path is not strictly cache-only in effect - `BaseWidgetUpdater` enqueues freshness work when the cached data is stale, and on first run may resolve a location - but it never blocks the render on a network response, so it always produces a visible update. Any future local-only trigger, such as sunrise/sunset day/night switching, redraws from cache first and goes to the network only when the data is actually stale.

### 4.3 Resolved Decision: Retain the redraw watchdog (keep `1800000`)

**Decision:** We will keep `updatePeriodMillis="1800000"` on all widget providers alongside WorkManager.

These are not two competing network schedulers. WorkManager is the primary periodic network-refresh mechanism. The provider callback is a **redraw and freshness watchdog**: it rebuilds the widget from cache, checks freshness, and enqueues refresh work only when the data is stale.

While Android explicitly recommends setting this to `0` when using WorkManager, real-world OEM battery killers (Samsung, Xiaomi, etc.) often aggressively defer WorkManager jobs. If a user keeps their screen on continuously (e.g., during navigation), WorkManager might stall, and screen-wake events won't fire, causing the widget to freeze.

The 30-minute host callback is the OS-level safety net for that case. It is not free, and the document should not claim otherwise: every callback wakes the app, reads Room and DataStore, decodes and renders icon bitmaps, and parcels a full `RemoteViews` update for each widget instance. Android documents full widget updates as computationally expensive. Multiply that by every installed widget, twice an hour, indefinitely.

The decision is therefore to keep the watchdog but make it cheap, and to measure it rather than assume either way:

- Redraw cached content only; never force a network request from the callback itself.
- Suppress routine fallback-content flashes.
- Batch the shared Room/DataStore reads across all widget IDs in a single pass.
- Let the freshness check and the repository mutex suppress redundant requests.
- Measure wakeup cost and `RemoteViews` payload size before and after.
- Re-test on the OEM device that exhibited the WorkManager deferral.

`WidgetUpdatePeriodTest` remains correct and should not be altered.

**Evidence note.** The freeze is directly documented by commit `d252045`, which restored `1800000` and records the cause: "`updatePeriodMillis=0` meant Android never called `onUpdate` on its own, so the widget stayed frozen when the screen was continuously on." Commit `522ac4e` added the regression guard three minutes later. The specific "30-90 minute OEM deferral" figure in the test comment is documented rationale, not a captured measurement - treat it as a historical observation until logs or benchmarks exist.

Two related items are unblocked and also reduce watchdog cost:

- Schedule periodic work and passive location tracking only while at least one weather widget exists, unless background refresh is intentionally required for a separate non-widget feature.
- Share one cache/prefs snapshot across all widget instances in a redraw batch instead of rereading Room and DataStore for every widget ID.

Reference: [Advanced widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced)

## 5. Proposed Improvements

### 5.1 Android 12+ responsive `RemoteViews`

**Recommendation: proceed independently; this does not wait on section 4.**

Responsive layouts depend on rendering being reusable, not on location semantics or scheduler ownership. The only prerequisite is the view-builder refactor listed below, so this can be built in parallel with everything in section 4. The current resize callback already rebuilds a widget immediately, so this is a smoother-resizing and system-health improvement rather than a correctness fix.

Use a small set of dp breakpoints for each existing provider. Preserve the semantic difference between Compact, Extended and Forecast widgets rather than making every provider expose every possible content set without a product decision.

Example content tiers:

- **Small:** time and current temperature.
- **Medium:** time, condition, temperature, high/low and location.
- **Large forecast-capable provider:** time, current weather and five-day row.

Implementation requirements:

- Refactor view creation so one fully bound `RemoteViews` can be produced per size without immediately calling `updateAppWidget()`.
- Bind clicks, clock formats, theme, visibility and weather data independently on every mapped view.
- Keep the current single-layout path for API 26-30.
- Verify at API 26 (oldest supported), API 30 (final pre-12 behaviour) and API 31+ (responsive path).
- Test the documented min/max dp range of every provider, including portrait, landscape and foldable size lists.

### 5.2 Pre-12 clock size variants

**Recommendation: low priority; implement only if device testing shows material user value.**

The problem is geometry, not lack of remote text-size support. The 29dp clippers and 58dp two-digit `TextClock` widths are coupled to the 48dp font. Changing one value independently breaks digit extraction.

Because `widget_clock_block.xml` is included inside shared and provider root layouts, selecting a standalone clock-block resource at runtime is insufficient. Valid options are:

- Complete root layout variants for each provider and supported clock size, or
- Multiple complete clock blocks embedded in the root layout with one selected by visibility.

Prefer the approach with the smallest tested XML surface. Four sizes across three provider roots can otherwise create substantial duplication. Preview layouts must remain visually consistent with runtime layouts.

### 5.3 Temperature behaviour between network refreshes

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

**Recommendation: calculate display state locally; avoid exact alarms by default.**

A redraw alone cannot currently change the icon because the cached `currentWeather.weatherCondition` already contains the day/night variant chosen at fetch time.

First add a display-layer mapping that:

- Finds today's sunrise and sunset in the device timezone (section 4.1).
- Determines whether the reference instant is day or night.
- Converts only conditions with meaningful day/night pairs, such as clear or partly cloudy.
- Leaves weather phenomena without separate variants unchanged.

Then refresh the icon on the next normal cache-only redraw. If closer timing is demonstrably important, schedule an inexact alarm/window and measure the benefit. An exact alarm adds permission, denial, reboot, timezone-change, stale-alarm and multi-widget lifecycle handling, and Android recommends exact alarms only for genuinely time-critical user-facing functionality.

Reference: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)

### 5.5 Tabular digits

**Recommendation: optional, low priority.**

The runtime clock already requests the generic monospace family, which should provide equal glyph advances. Adding `android:fontFeatureSettings="tnum"` is a reasonable defensive hint, but it is effective only when the selected font supports that OpenType feature and should not be described as a universal OEM guarantee.

Apply it consistently to runtime and preview clock text, then verify on devices or emulators that previously demonstrated digit jitter.

### 5.6 Bitmap icon caching

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

| Work item | Priority | Complexity | Expected value | Dependency |
| :--- | :--- | :--- | :--- | :--- |
| Screen wake: cached redraw before freshness-gated refresh | **P0 (build next)** | Low | Fixes the no-update-when-offline wake | None |
| Reduce watchdog cost, then measure it | **P1** | Low-Medium | Cheaper 30-minute callback; evidence for any later change | Batching |
| Gate background work on active-widget existence | **P1** | Low-Medium | Less background work with no widget installed | None |
| Batch cache/prefs reads across a redraw | **P1** | Low | Fewer redundant Room/DataStore reads per update | None |
| Android 12+ responsive layouts | **P1 (parallel)** | Medium | Smoother resizing and better size-specific UX | Reusable view builder only |
| Local day/night display mapping | **P2** | Low-Medium | More timely visual state | Screen-wake redraw |
| Pre-12 layout variants | **P2** | Medium-High | Older-device size customization | Device evidence |
| Forecast-derived temperature fallback/interpolation | **P2/P3** | Medium | Cosmetic progression with accuracy trade-off | Screen-wake redraw and product policy |
| Tabular-number hint | **P3** | Minimal | Small OEM typography defence | Reproduction evidence |
| Bitmap LRU cache | **P3** | Low-Medium | Possible allocation reduction | Profiling evidence |
| Glance migration | **Research** | High | Unknown until parity spike | Stable feature requirements |
| Persist `appWidgetId -> locationId` | **Deferred - 4.1 phase 2** | Medium | Foundation for multi-city | Section 4.1 phase 2 |
| Wire `WidgetConfigActivity` (`android:configure`, save, initial update) | **Deferred - 4.1 phase 2** | Medium | Lets each widget pick its city | Persistence |
| Route detail screen by `appWidgetId` / `locationId` | **Deferred - 4.1 phase 2** | Medium | Completes multi-city click behaviour | Persistence |
| World-clock timezone pipeline | **Not planned** | High | Out of scope; see 4.1 phase 3 | - |

## 7. Verification Requirements

Every implementation should follow red-green-refactor with real objects and run the full test suite. In addition:

- Add contract tests for no-widget behaviour: no periodic work and no passive location tracking while zero widgets are installed.
- Test that a screen wake with no network still redraws cached content, and that a wake with fresh data issues no request.
- Test date and forecast-day rendering around midnight and across DST gaps/overlaps in the device timezone.
- Test that the `"auto"` timezone sentinel is never passed to `ZoneId.of()`.
- *(Phase 2)* Persistence and deletion tests for every `appWidgetId -> locationId` mapping, including `onDeleted()` cleanup.
- *(Phase 2)* Two widget IDs assigned to different locations render different weather and route to different detail targets.
- *(Phase 2)* An unassigned widget falls back to the primary location; a widget whose assigned location was deleted degrades predictably.
- Test responsive layout selection at every dp breakpoint and at the provider min/max sizes.
- Test cache-only redraw while offline.
- Test day/night mapping immediately before and after sunrise/sunset.
- Test pre-12 layouts at API 26 and API 30, plus API 31+ for the responsive path, with Robolectric coverage and real launcher hosts where clipping behaviour matters. `minSdk` is 26, so API 30 alone is not sufficient pre-12 coverage.
- Measure bitmap allocation and RemoteViews payloads before accepting caching as an optimization.
- Visually verify Android 8 (API 26), Android 10/11, Android 12+, at least one high-density device, and an OEM launcher previously affected by vector inflation.
