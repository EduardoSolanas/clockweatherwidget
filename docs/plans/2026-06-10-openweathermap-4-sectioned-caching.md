# OpenWeatherMap One Call 4.0 migration + per-section caching

**Date:** 2026-06-10
**Status:** Planned

## Background / problem

One Call API 3.0 (`/data/3.0/onecall`) returns current + 48h hourly + 8-day daily in **one call**.
One Call API 4.0 splits this into separate endpoints, so a naive migration would cost **3 calls per refresh**:

| Section | 4.0 endpoint | Notes |
|---|---|---|
| Current | `GET /data/4.0/onecall/current?lat&lon&appid&units` | Real-time nowcast, model updated ~every 10 min |
| Hourly  | `GET /data/4.0/onecall/timeline/1h?lat&lon&appid&units` | Up to **20 records per response**; `next` URL in response paginates further |
| Daily   | `GET /data/4.0/onecall/timeline/1day?lat&lon&appid&units` | Up to **10 records per response**; `next` URL paginates further |

Docs: https://openweathermap.org/api/one-call-4

Key facts that drive the design:

1. **The current endpoint is instant, not cached-by-hour.** It is OWM's nowcast, refreshed roughly every 10 minutes server-side. It is NOT "the 00:00 value for today" and NOT the current-hour slot of the forecast. So it is safe (and correct) to use it as the "now" tile and as the first point of the 24h view.
2. **Forecast sections change slowly.** Hourly forecasts only meaningfully change when the hour rolls over or the model re-runs; daily forecasts only need a refresh when the *local* day changes (or after several hours).
3. Therefore: per-section TTLs mean the typical app-open costs **1 call** (current only), with hourly added at most once per hour and daily at most a few times per day. Worst case (cold cache, 14-day view) is 5 calls: 1 current + 2 hourly pages (24 records) + 2 daily pages (14 records).

### Answering the design questions that prompted this

- *"Will current weather be the instant one or cached from 00:01 / delayed?"* → Instant (≤10 min old). Use it as the "now" card and current-hour value.
- *"Store current day in DB and flush daily cache when day changes?"* → Yes, but store the **local date at the location** (use the location's timezone, not device timezone) per section, in a small metadata table — see Phase 2. Don't literally "flush" the rows on day change; just mark the section stale so the old data still renders while the refresh runs (existing offline-first behavior is preserved).
- *"Only 1 call per app open?"* → Yes in the common case: current is always refetched if older than 10 min; hourly/daily refetch only when their own staleness rules trip.

## Current architecture (what exists today)

- `WeatherDataProvider.fetchWeatherData(location, forecastDays): WeatherData` — single-shot interface, 3 implementations (OpenWeatherMap, OpenMeteo, Google).
- `WeatherRepositoryImpl` (app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt):
  - `getWeatherData()` — Room flow combining `current_weather`, `hourly_forecasts`, `daily_forecasts`, `locations`.
  - `ensureFreshWeatherData()` — checks `isWeatherDataFresh()` (app/src/main/java/com/clockweather/app/domain/model/WeatherFreshness.kt) and does a **full** refresh if stale.
  - `forceRefreshWeatherData()` — always full refresh. Called by `WeatherUpdateWorker` (background widget updates!) and Settings.
- `isWeatherDataFresh()` is all-or-nothing: any stale section ⇒ refetch everything.
- Room `WeatherDatabase` version 3.

## Design

### Per-section staleness rules

| Section | Stale when… | Typical call frequency |
|---|---|---|
| `CURRENT` | fetched > 10 min ago | every app open / widget tick |
| `HOURLY` | first cached future hour ≠ current hour at location, **or** fetched > 60 min ago, **or** < 24 future hours cached | ≤ 1/hour |
| `DAILY` | local date at location ≠ `fetchedLocalDate`, **or** fetched > 6 h ago, **or** fewer than `forecastDays` future days cached | a few/day |

"Local date at location" = `Instant.now()` converted using the location's zone (the codebase already has `locationReferenceDateTime()` in the domain model — reuse it).

### Section-aware provider interface (backward compatible)

Extend `WeatherDataProvider` with optional per-section methods. Default implementations delegate to the existing full fetch so **OpenMeteo and Google providers need zero changes**:

```kotlin
enum class WeatherSection { CURRENT, HOURLY, DAILY }

interface WeatherDataProvider {
    suspend fun fetchWeatherData(location: Location, forecastDays: Int): WeatherData

    /** True if the provider can fetch sections independently (OWM 4.0). */
    val supportsSectionedFetch: Boolean get() = false

    /** Only called when [supportsSectionedFetch] is true. Returns a WeatherData
     *  with ONLY the requested sections populated; others empty. */
    suspend fun fetchSections(
        location: Location,
        sections: Set<WeatherSection>,
        forecastDays: Int
    ): WeatherData = fetchWeatherData(location, forecastDays)
}
```

### Repository behavior

- `ensureFreshWeatherData`: compute the set of stale sections. If empty → return. If provider `supportsSectionedFetch` → `fetchSections(staleSections)`, persist only those sections + their metadata rows. Otherwise → existing full-fetch path (persist all, stamp all metadata).
- `forceRefreshWeatherData`: keep as full refresh of all sections (pull-to-refresh / settings change semantics).
- **Change `WeatherUpdateWorker` to call `ensureFreshWeatherData` instead of `forceRefreshWeatherData`** (app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt:34). This is the single biggest call-saver: background widget ticks will usually fetch only `CURRENT`. Verify nothing depends on the worker unconditionally hitting the network; the widget renders from Room either way.
- Day-change-on-open case from the original idea is automatically handled: `WeatherDetailViewModel` already triggers `ensureFresh` on open; with the new rules, a day rollover marks `DAILY` stale and refreshes the 7/14-day container.

### Persistence of fetch metadata

New Room table (DB version 3 → 4):

```sql
CREATE TABLE IF NOT EXISTS weather_fetch_metadata (
    locationId INTEGER NOT NULL,
    section TEXT NOT NULL,            -- 'CURRENT' | 'HOURLY' | 'DAILY'
    fetchedAtUtc INTEGER NOT NULL,    -- epoch millis
    fetchedLocalDate TEXT NOT NULL,   -- ISO yyyy-MM-dd at the location's timezone
    provider TEXT NOT NULL,           -- WeatherProviderType name
    PRIMARY KEY(locationId, section)
)
```

Notes:
- Stamp metadata inside the same `database.withTransaction { }` as the data writes.
- If `provider` in metadata ≠ currently selected provider, treat ALL sections as stale (switching providers must not mix data sources).
- Destructive-migration is not acceptable; write a real `Migration(3, 4)` following the existing pattern in `WeatherDatabase.kt`.

### OWM 4.0 API layer

Replace the single retrofit method in `OpenWeatherMapApi.kt`:

```kotlin
@GET("data/4.0/onecall/current")
suspend fun getCurrent(@Query("lat") lat: Double, @Query("lon") lon: Double,
    @Query("appid") apiKey: String, @Query("units") units: String = "metric"): Owm4CurrentResponseDto

@GET("data/4.0/onecall/timeline/1h")
suspend fun getHourly(@Query("lat") lat: Double, @Query("lon") lon: Double,
    @Query("appid") apiKey: String, @Query("units") units: String = "metric",
    @Query("start") start: Long? = null, @Query("cnt") cnt: Int? = null): Owm4TimelineResponseDto

@GET("data/4.0/onecall/timeline/1day")
suspend fun getDaily(@Query("lat") lat: Double, @Query("lon") lon: Double,
    @Query("appid") apiKey: String, @Query("units") units: String = "metric",
    @Query("start") start: Long? = null, @Query("cnt") cnt: Int? = null): Owm4TimelineResponseDto
```

Pagination: responses cap at 20 hourly / 10 daily records and include `next`/`prev` URLs. **Implementer must verify the actual JSON shape against the live API or docs before writing DTOs** (the public docs page is sparse). Strategy:
1. Try `cnt=24` (hourly) / `cnt=14` (daily) on the first request — `cnt` appears in OWM's own pagination URLs, it may be honored up to the cap.
2. If the response returns fewer records than needed, issue ONE follow-up call using the `start` value derived from the last received record's timestamp + 1 step (or parse the `next` URL's query params — do not blindly GET an absolute URL through retrofit; extract `start`/`cnt` and call the same endpoint).
3. Hard cap: never more than 2 pages per section per refresh.

Also handle:
- 4.0 requires the "One Call by Call" subscription. Keep the existing provider-fallback path in `WeatherRepositoryImpl.fetchWithFallback` so a 401/403 falls back to the default provider.
- If 3.0 is still alive when implementing, consider keeping `getOneCall` as a code path behind a constant for easy rollback; delete once 4.0 is confirmed working in production.

### Mapper

- New DTOs under `data/remote/dto/openweathermap/` for the current and timeline responses. **Field names must be confirmed from a real response** (use a scratch curl with the dev API key); expect a structure similar to 3.0's `current`/`hourly[]`/`daily[]` objects but verify temp min/max, pop, weather[].id, sunrise/sunset, uvi, visibility, wind_gust, and the alert-id references.
- Extend `OpenWeatherMapMapper` with `mapCurrent(...)`, `mapHourly(...)`, `mapDaily(...)` producing the same domain models as today (reuse the existing condition-code → `WeatherCondition` mapping). Keep `mapToWeatherData` for tests until the 3.0 path is deleted.
- For the 24h graph: the app already merges current + hourly; first hourly slot is the *forecast* for the current hour and may differ slightly from the observed current — the existing UI behavior (current card from `CURRENT`, graph from hourly) is fine, no change needed.

## Implementation phases

### Phase 1 — Domain: per-section freshness
1. Add `WeatherSection` enum (domain layer).
2. Refactor `WeatherFreshness.kt`: split `isWeatherDataFresh` into `staleSections(weather, metadata, referenceDateTime, requiredForecastDays): Set<WeatherSection>` implementing the staleness table above. Keep a thin `isWeatherDataFresh` = `staleSections(...).isEmpty()` for compatibility.
3. Unit tests: hour rollover marks only HOURLY stale; midnight rollover (in location TZ, test a TZ ≠ device TZ) marks DAILY (+HOURLY/CURRENT per TTL); provider mismatch marks all stale; null metadata marks all stale.

### Phase 2 — Persistence: metadata table
1. `WeatherFetchMetadataEntity` + `WeatherFetchMetadataDao` (get by locationId, upsert per section, delete by locationId).
2. `WeatherDatabase` v4 + `MIGRATION_3_4` (SQL above). Add the entity to `@Database`.
3. Stamp metadata in `persistWeatherData`; pass which sections were written.
4. Tests: migration test if the project has Room migration tests; DAO round-trip test otherwise.

### Phase 3 — Repository: sectioned refresh
1. Extend `WeatherDataProvider` with `supportsSectionedFetch` + `fetchSections` (defaults as shown — no changes to OpenMeteo/Google).
2. `WeatherRepositoryImpl.ensureFreshWeatherData`: compute stale sections via Phase 1; sectioned fetch when supported; persist only fetched sections (current upsert; hourly/daily delete+insert only when that section was fetched); stamp metadata for fetched sections only.
3. Fallback semantics: if the selected provider fails mid-sections, the fallback provider does a **full** fetch (it doesn't support sections) — that's fine, stamp all sections with the fallback provider name.
4. Switch `WeatherUpdateWorker` to `ensureFreshWeatherData`. Keep `forceRefresh` for user-initiated refresh in Settings/detail screen. Also apply the worker hardening described in `2026-06-10-onecall-3-call-reduction.md` Step 2b (per-location failure isolation, cancel periodic work when last weather widget is removed, explicit backoff) — those changes are API-version-independent.
5. Tests: repository test that only stale sections hit the provider; worker no longer forces (update existing worker test if any); force path still refreshes everything.

### Phase 4 — OWM 4.0 provider
1. Capture real responses from the three endpoints with the dev key; write DTOs to match.
2. New retrofit methods (above); implement `fetchSections` in `OpenWeatherMapProvider` (`supportsSectionedFetch = true`), with the ≤2-page pagination strategy; `fetchWeatherData` = `fetchSections(all sections)`.
3. Mapper functions + unit tests using recorded JSON fixtures (follow the existing `OpenWeatherMapProviderTest` / `OpenWeatherMapMapperTest` patterns).
4. Update `WeatherProviderType.maxForecastDays` for OWM if 4.0 daily supports >8 days (it does — up to 1.5 years; cap at the app's max view, 14).

### Phase 5 — Verification
1. `.\gradlew.bat testDebugUnitTest` green.
2. Manual: fresh install → open detail screen → exactly 1 current + paged hourly + paged daily calls (verify via OkHttp logging). Reopen within 10 min → 0 calls. Reopen after 10 min → 1 call (current only). Fake a day change (device date or test hook) → daily refreshes.
3. Confirm widget background tick fetches only CURRENT when forecasts are fresh.

## Edge cases checklist for the implementer

- Location timezone vs device timezone everywhere a "day" or "hour" is compared (reuse `locationReferenceDateTime()`).
- Multiple locations: metadata is per-locationId; `refreshMutex` currently serializes all refreshes — keep as is.
- Offline: stale sections + network failure must not wipe cached rows (only delete+insert after a successful fetch — current code already does this inside the success path; preserve that).
- `forecastDays` increase (7 → 14 in settings): DAILY staleness rule's "fewer than forecastDays future days" covers it.
- Don't regress the other two providers: all their tests must pass untouched.

## Expected call budget (OWM, per location)

- App open, warm cache, same hour/day: **1 call** (current) — or 0 if < 10 min.
- App open after hour rollover: 2–3 calls (current + hourly pages).
- App open after day rollover: up to 5 calls (current + 2 hourly + 2 daily) — a few times/day worst case.
- Background widget tick: usually **1 call**.
