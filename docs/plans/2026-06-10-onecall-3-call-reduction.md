# One Call 3.0: keep single-call API, reduce call frequency with smarter caching

**Date:** 2026-06-10
**Status:** Planned (alternative to `2026-06-04-openweathermap-4-sectioned-caching.md` — implement ONE of the two, not both)

## Background

One Call 3.0 (`/data/3.0/onecall`) returns current + 48h hourly + 8-day daily in **one call**, so unlike the 4.0 plan there are no per-section calls to split. The only lever for reducing API usage is **how often we make that one call**. Everything already renders from the Room cache (offline-first), so this plan only changes the *decision* of when to refresh.

Desired behavior (from the product owner):
- Opening the weather app should show fresh current weather…
- …but skip the network call entirely when there is no point (data still fresh).

OpenWeather updates its current-conditions model roughly every 10 minutes, so calling more often than that returns identical data. That gives us the core rule: **a refresh is "worth it" only if the cached current weather is older than 10 minutes** — with two extra triggers (hour rollover for the 24h graph, day rollover for the 7/14-day view) that in practice are already covered by the 10-minute rule but must be kept for correctness after long idle periods.

## What's wrong today

1. `isWeatherDataFresh()` (app/src/main/java/com/clockweather/app/domain/model/WeatherFreshness.kt) uses `maxAgeMinutes = 30`. Fine, but it means a user opening the app 12 minutes after the last refresh sees up-to-30-min-old "current" weather. Product wants fresher current on open.
2. **`WeatherUpdateWorker` calls `forceRefreshWeatherData`** (app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt:34), which bypasses freshness checks entirely. Every background widget tick is an unconditional API call, even if the app refreshed 30 seconds earlier. This is the single biggest source of wasted calls.
3. Settings (`SettingsViewModel.kt:355`) also force-refreshes; that one is user-initiated and should stay forced.
4. There is no record of *when* we fetched other than `currentWeather.lastUpdated` — adequate for 3.0 (one call stamps everything), so **no DB migration is needed** in this plan. (Note: `lastUpdated` must be the time WE fetched, not the API's `current.dt` — verify in `OpenWeatherMapMapper`/`WeatherEntityMapper`; if it stores the API's `dt`, the 10-min TTL gets skewed by up to 10 min. If so, stamp fetch time at persist instead.)

## Design

### One freshness rule, three triggers

Refactor `isWeatherDataFresh` so the refresh decision is: **call the API if ANY of these is true**, otherwise serve cache:

| Trigger | Rule | Why |
|---|---|---|
| Current stale | `currentWeather.lastUpdated` older than **10 min** (configurable constant, `CURRENT_MAX_AGE_MINUTES = 10`) | Matches OWM's model update cadence; calling sooner returns the same data |
| Hour rollover | first cached future hourly slot ≠ current hour at the location | 24h graph must start at "now"; existing check, keep it |
| Day rollover / coverage | fewer than `forecastDays` distinct future days cached, or cached data is from a previous local date at the location | 7/14-day view must roll over at local midnight; existing checks, keep them (this is the "store the day" idea — `lastUpdated.toLocalDate()` vs today already encodes it, no extra DB field needed) |

Since 3.0 returns everything in one call, any single trigger firing refreshes all three views at once — the day-change flush of the 7/14-day container the product owner asked for falls out automatically.

Important: keep using `locationReferenceDateTime()` (location's timezone) for the hour/day comparisons, not device time.

### Where refreshes are triggered

| Caller | Today | After |
|---|---|---|
| Weather app open (`WeatherDetailViewModel` → `RefreshWeatherUseCase.ensureFresh`) | ensureFresh (30-min TTL) | ensureFresh (10-min TTL) — fresh current on open, **0 calls** if opened again within 10 min |
| Background widget tick (`WeatherUpdateWorker`) | **forceRefresh — unconditional call** | `ensureFresh` — call only if >10 min old / rollover |
| Pull-to-refresh / settings change | forceRefresh | forceRefresh (unchanged — explicit user intent) |

### Optional micro-optimizations (cheap, do if trivial)

- The API request already excludes `minutely,alerts`. Leave as is; `exclude` doesn't reduce call count, only payload.
- Widget tick interval: if `WeatherUpdateScheduler` runs more often than every 15 min, the new ensureFresh gate makes extra ticks free (no network), so no schedule change is required — but document that the effective network cadence is now max ~6 calls/hour/location regardless of tick rate.

## Implementation steps

### Step 1 — Freshness constant + tighter current TTL
1. In `WeatherFreshness.kt`, change the default `maxAgeMinutes` to a named constant `CURRENT_MAX_AGE_MINUTES = 10L` (keep the parameter so tests can override).
2. Verify what `currentWeather.lastUpdated` actually stores (mapper code). If it is the API's observation timestamp (`current.dt`), change persistence to stamp the local fetch time (or store both — fetch time for TTL, `dt` for display). This is a behavior-correctness prerequisite for a 10-min TTL.
3. Update/extend unit tests for `isWeatherDataFresh`: fresh at 9 min, stale at 11 min; hour-rollover trigger; day-rollover trigger with a location timezone different from device timezone.

### Step 2 — Stop the worker from force-refreshing
1. In `WeatherUpdateWorker.kt:34`, replace `forceRefreshWeatherData(location, forecastDays)` with `ensureFreshWeatherData(location, forecastDays)`.
2. Check the worker's error/Result handling still makes sense when no network call happens (ensureFresh returning without fetching is success).
3. Update any worker unit tests accordingly.

### Step 2b — Widget worker improvements (beyond the force→ensure switch)

Current worker code: `WeatherUpdateWorker.kt`, scheduling in `WeatherUpdateScheduler.kt`. Four problems besides the unconditional refresh:

1. **One failing location poisons all locations.** `locations.forEach { forceRefresh }` — if location #2 throws, the whole worker retries (up to 3×), re-fetching location #1 each time. Fix: wrap each location in its own try/catch, collect failures, and only return `Result.retry()` if at least one location failed (the ensureFresh gate makes re-runs of already-succeeded locations free, so retry becomes cheap):

```kotlin
var anyFailure = false
locations.forEach { location ->
    try {
        weatherRepository.ensureFreshWeatherData(location, forecastDays)
    } catch (e: Exception) {
        Log.w(TAG, "Refresh failed for ${location.id}", e)
        anyFailure = true
    }
}
app?.refreshAllWidgets(applicationContext)   // always redraw from cache, even on partial failure
return if (!anyFailure) Result.success()
       else if (runAttemptCount < 3) Result.retry() else Result.failure()
```

2. **Worker runs even when no weather widgets exist.** Verify whether anything cancels the periodic work when the last widget is removed. If not: in each widget provider's `onDisabled()`, check whether any other weather-showing widget type is still placed (via `AppWidgetManager.getAppWidgetIds` for each provider class); if none, call `WeatherUpdateScheduler.cancel(context)`. In `onEnabled()`, call `ensureScheduled(...)`. (If the app itself also relies on this worker while open, skip this item — confirm before implementing. Clock-only widgets don't need weather fetches; only cancel if NO weather widget remains.)

3. **`scheduleImmediateRefresh` reuses the same worker** (used on widget add / boot). After the force→ensure switch this becomes a freshness-gated refresh — correct behavior (a just-added widget renders instantly from cache and only fetches if stale). No change needed, but update its doc comment to say it no longer guarantees a network fetch.

4. **Explicit backoff.** Add `.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)` to both work requests in the scheduler so retry behavior is deliberate rather than default-dependent.

Tests: worker test for partial failure (location 1 succeeds, location 2 throws → retry returned, location 1 not re-fetched on the retry because it is now fresh); scheduler cancel-on-last-widget-removed if item 2 applies.

> These worker changes apply identically if the 4.0 sectioned plan is chosen instead — implement them once in whichever plan is executed.

### Step 3 — Verify app-open path
1. Confirm `WeatherDetailViewModel` calls `ensureFresh` on screen open (it should already); if it calls `forceRefresh` anywhere except an explicit pull-to-refresh gesture, switch to `ensureFresh`.
2. Pull-to-refresh (if present) and the Settings provider-change path stay on `forceRefresh`.

### Step 4 — Verification
1. `.\gradlew.bat testDebugUnitTest` green.
2. Manual with OkHttp logging: open app → 1 call; reopen within 10 min → 0 calls; reopen after 10 min → 1 call; widget tick right after an app open → 0 calls; change device date forward a day → next open refreshes the 7/14-day view.

## Expected call budget (per location)

The gate caps network at **max ~6 calls/hour** no matter how often the app opens or the widget ticks.

- Heavy day (widget every 30 min + 10 app opens): old ≈ 50+ calls → new ≈ 48 worst case, typically **~30** (widget ticks that land within 10 min of an app open are free, and vice versa).
- Light day (widget only): one call per tick → one call per tick *only if* >10 min elapsed; with a 30-min tick, unchanged (~48), with a 15-min tick, halved.
- Either way comfortably inside the 1,000/day free tier with multiple locations.

## Out of scope

- No DB migration, no new tables, no provider-interface changes, no DTO changes.
- OpenMeteo and Google providers benefit automatically (same repository gate) — their tests must pass untouched.
- If OWM later hard-deprecates 3.0, switch to the 4.0 sectioned plan; nothing here conflicts with it (the worker fix and TTL work carry over).
