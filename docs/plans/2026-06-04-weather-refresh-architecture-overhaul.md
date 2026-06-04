# Weather Refresh Architecture Overhaul

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Unify weather data refresh so widget and detail screen always show consistent data with minimal network calls.

**Architecture:** Single refresh path through the repository. Widgets never make network calls — they read from DB. A staleness check in the repository prevents redundant API calls. The periodic worker is the sole driver of network refreshes.

**Tech Stack:** Kotlin, Room DB, Hilt DI, WorkManager, DataStore

---

## Current Problems

### 1. Two refresh methods cause inconsistency

```
WeatherRepository
├── refreshWeatherData()      → full fetch WITH hourly (detail screen, worker)
└── refreshWidgetWeatherData() → lightweight fetch WITHOUT hourly (widgets)
```

When a widget refreshes, it overwrites the DB with empty hourly data. The detail screen then reads from DB and shows no hourly graph.

### 2. Multiple network triggers

| Trigger | Network call? | Problem |
|---------|---------------|---------|
| `WeatherUpdateWorker` (periodic) | Yes, via `refreshWeatherData()` | Correct |
| `WeatherDetailViewModel.refresh()` | Yes, via `refreshWeatherData()` | Correct, but then triggers widget refresh |
| `SettingsViewModel.setWeatherProvider()` | Yes, via `refreshWeatherData()` | Correct, but then triggers widget refresh + immediate worker |
| `BaseWidgetUpdater.updateWidget()` | Yes, if stale >30min | **Redundant** — each widget makes its own call |
| `BaseWidgetProvider.onUpdate()` | Yes, via `updateWidget()` | **Redundant** — system triggers this |
| `TimeChangedReceiver` | No (after fix) | Correct |
| `PackageReplacedReceiver` | No (after fix) | Correct |

### 3. Inconsistent provider behavior

| Provider | `fetchWidgetWeatherData()` includes hourly? |
|----------|---------------------------------------------|
| OpenMeteo | Yes (delegates to `fetchWeatherData()`) |
| OpenWeatherMap | Yes (after recent fix) |
| Google | **No** (explicitly skips hourly) |

### 4. The `allowWeatherRefresh` parameter is a band-aid

The `updateWidget(allowWeatherRefresh: Boolean)` parameter was added to prevent widgets from making network calls when triggered by the detail screen. But it's fragile — callers must remember to pass `false`.

---

## Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Network Refresh Drivers                   │
├─────────────────────────────────────────────────────────────┤
│  WeatherUpdateWorker (periodic, every 15-30 min)            │
│  WeatherDetailViewModel.refresh() (user-initiated)          │
│  SettingsViewModel.setWeatherProvider() (user-initiated)    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   WeatherRepository                          │
├─────────────────────────────────────────────────────────────┤
│  refreshWeatherData(location, forecastDays)                  │
│    ├── Check staleness (lastUpdated < 30 min ago?)          │
│    ├── If fresh: skip network, return cached                │
│    └── If stale: fetch from provider → persist to DB        │
│                                                              │
│  getWeatherData(location): Flow<WeatherData?>               │
│    └── Read from DB (reactive Flow)                         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Consumers                               │
├─────────────────────────────────────────────────────────────┤
│  Widgets: getWeatherData() → read from DB, no network       │
│  Detail screen: getWeatherData() → read from DB, no network │
└─────────────────────────────────────────────────────────────┘
```

**Key principles:**
1. **Widgets NEVER make network calls** — they read from DB
2. **Single refresh method** — no separate "widget" vs "detail" refresh
3. **Staleness check in repository** — prevents redundant API calls
4. **Consistent provider behavior** — all providers return the same data shape

---

## Implementation Tasks

### Task 1: Add staleness tracking to WeatherRepository

**Objective:** Track when weather data was last fetched so the repository can decide whether to hit the network.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt`
- Modify: `app/src/main/java/com/clockweather/app/domain/repository/WeatherRepository.kt`

**Step 1: Add last-refresh timestamp to DataStore**

```kotlin
// In WeatherRepositoryImpl.kt
private val KEY_LAST_REFRESH = longPreferencesKey("last_weather_refresh_ms")

private suspend fun getLastRefreshTime(): Long =
    dataStore.data.first()[KEY_LAST_REFRESH] ?: 0L

private suspend fun setLastRefreshTime(timeMs: Long) {
    dataStore.edit { it[KEY_LAST_REFRESH] = timeMs }
}
```

**Step 2: Add staleness check method**

```kotlin
private suspend fun isWeatherStale(): Boolean {
    val lastRefresh = getLastRefreshTime()
    val ageMinutes = (System.currentTimeMillis() - lastRefresh) / 60_000
    return ageMinutes >= 30 // 30-minute staleness threshold
}
```

**Step 3: Update refreshWeatherData to check staleness**

```kotlin
override suspend fun refreshWeatherData(
    location: Location,
    forecastDays: Int,
    forceRefresh: Boolean = false
) {
    refreshMutex.withLock {
        if (!forceRefresh && !isWeatherStale()) {
            Log.d(TAG, "Weather data is fresh, skipping network refresh")
            return
        }

        val providerType = WeatherProviderPreferences.resolve(
            dataStore.data.first()[WeatherProviderPreferences.KEY_WEATHER_PROVIDER]
        )
        val weatherData = fetchWithFallback(providerType) { provider, actualProviderType ->
            provider.fetchWeatherData(
                location = location,
                forecastDays = forecastDays.coerceIn(1, actualProviderType.maxForecastDays)
            )
        }
        persistWeatherData(weatherData.normalizeDailyConditions(), location.id)
        setLastRefreshTime(System.currentTimeMillis())
    }
}
```

**Step 4: Update interface**

```kotlin
// In WeatherRepository.kt
interface WeatherRepository {
    fun getWeatherData(location: Location): Flow<WeatherData?>
    suspend fun refreshWeatherData(
        location: Location,
        forecastDays: Int = 7,
        forceRefresh: Boolean = false
    )
}
```

**Step 5: Write tests**

```kotlin
// In WeatherRepositoryStalenessTest.kt
@Test
fun `refreshWeatherData skips network when data is fresh`() = runTest {
    // Setup: last refresh was 5 minutes ago
    // Action: call refreshWeatherData(forceRefresh = false)
    // Verify: provider.fetchWeatherData() was NOT called
}

@Test
fun `refreshWeatherData hits network when data is stale`() = runTest {
    // Setup: last refresh was 35 minutes ago
    // Action: call refreshWeatherData(forceRefresh = false)
    // Verify: provider.fetchWeatherData() WAS called
}

@Test
fun `refreshWeatherData hits network when forceRefresh is true`() = runTest {
    // Setup: last refresh was 5 minutes ago
    // Action: call refreshWeatherData(forceRefresh = true)
    // Verify: provider.fetchWeatherData() WAS called
}
```

**Step 6: Commit**

```bash
git add -A
git commit -m "feat: add staleness tracking to WeatherRepository

Track last refresh time in DataStore. Repository now checks staleness
before hitting the network, preventing redundant API calls when data
is already fresh (< 30 min old).

Added forceRefresh parameter to bypass staleness check for user-initiated
refreshes."
```

---

### Task 2: Remove refreshWidgetWeatherData from repository

**Objective:** Eliminate the separate "widget refresh" method. All refreshes go through `refreshWeatherData()`.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/domain/repository/WeatherRepository.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/WeatherDataProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/OpenMeteoWeatherProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/OpenWeatherMapProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/GoogleWeatherProvider.kt`

**Step 1: Remove from interface**

```kotlin
// In WeatherRepository.kt — remove this method:
// suspend fun refreshWidgetWeatherData(location: Location)
```

**Step 2: Remove from implementation**

```kotlin
// In WeatherRepositoryImpl.kt — remove the entire refreshWidgetWeatherData method
```

**Step 3: Remove fetchWidgetWeatherData from WeatherDataProvider interface**

```kotlin
// In WeatherDataProvider.kt — remove this method:
// suspend fun fetchWidgetWeatherData(location: Location): WeatherData
```

**Step 4: Remove from all providers**

```kotlin
// In OpenMeteoWeatherProvider.kt — remove fetchWidgetWeatherData override
// In OpenWeatherMapProvider.kt — remove fetchWidgetWeatherData override
// In GoogleWeatherProvider.kt — remove fetchWidgetWeatherData override
```

**Step 5: Update BaseWidgetUpdater to use refreshWeatherData**

```kotlin
// In BaseWidgetUpdater.kt, line ~226:
// Change: weatherRepo.refreshWidgetWeatherData(location)
// To: weatherRepo.refreshWeatherData(location, forceRefresh = true)
```

Wait — this is wrong. Widgets should NOT trigger network refreshes at all. Let me reconsider.

**Revised Step 5: Remove network refresh from widgets entirely**

```kotlin
// In BaseWidgetUpdater.kt, remove the shouldRefreshWeather check and network call:
// Lines ~222-231 should become:
val weather = weatherRepo.getWeatherData(location).first()
// No network call — widgets just read from DB
```

**Step 6: Update tests**

- Remove tests for `refreshWidgetWeatherData`
- Remove tests for `fetchWidgetWeatherData`
- Update `BaseWidgetUpdaterTest` to verify widgets don't make network calls

**Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove separate widget refresh path

Eliminate refreshWidgetWeatherData() and fetchWidgetWeatherData().
All refreshes now go through refreshWeatherData() with staleness check.

Widgets no longer make network calls — they read from DB only.
The periodic worker is the sole driver of network refreshes."
```

---

### Task 3: Remove allowWeatherRefresh parameter from updateWidget

**Objective:** Simplify the widget update API. Widgets never make network calls, so the parameter is unnecessary.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`
- Modify: `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt`
- Modify: `app/src/test/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdaterTest.kt`

**Step 1: Remove parameter from updateWidget**

```kotlin
// In BaseWidgetUpdater.kt:
suspend fun updateWidget(appWidgetId: Int) {
    // Remove allowWeatherRefresh parameter
    // Remove shouldRefreshWeather check
    // Remove network call
}
```

**Step 2: Update all callers**

```kotlin
// In BaseWidgetProvider.kt:
updater.updateWidget(it)  // Remove allowWeatherRefresh = false

// In ClockWeatherApplication.kt:
updater.updateWidget(id)  // Already correct after Task 2

// In BaseWidgetUpdaterTest.kt:
// Update all test calls to remove the parameter
```

**Step 3: Remove shouldRefreshWeather function**

```kotlin
// In BaseWidgetUpdater.kt — remove the entire shouldRefreshWeather function
// It's no longer needed since widgets don't refresh
```

**Step 4: Update tests**

```kotlin
// Remove tests for shouldRefreshWeather
// Update BaseWidgetUpdaterTest to verify no network calls
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove allowWeatherRefresh parameter from updateWidget

Widgets never make network calls, so the parameter is unnecessary.
Simplified the widget update API."
```

---

### Task 4: Update WeatherUpdateWorker to use forceRefresh

**Objective:** The periodic worker should always force a refresh, bypassing the staleness check.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt`
- Modify: `app/src/test/java/com/clockweather/app/worker/WeatherUpdateWorkerTest.kt`

**Step 1: Update worker to force refresh**

```kotlin
// In WeatherUpdateWorker.kt:
override suspend fun doWork(): Result {
    return try {
        val prefs = dataStore.data.first()
        val forecastDays = prefs[SettingsViewModel.KEY_FORECAST_DAYS] ?: 7

        val locations = locationRepository.getSavedLocations().first()
        locations.forEach { location ->
            weatherRepository.refreshWeatherData(
                location = location,
                forecastDays = forecastDays,
                forceRefresh = true  // Worker always forces refresh
            )
        }
        // Notify widgets to update (they'll read from DB, no network)
        val app = applicationContext as? ClockWeatherApplication
        app?.refreshAllWidgets(applicationContext)

        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Background weather update failed", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
```

**Step 2: Update tests**

```kotlin
// Verify worker calls refreshWeatherData with forceRefresh = true
```

**Step 3: Commit**

```bash
git add -A
git commit -m "feat: WeatherUpdateWorker forces refresh

The periodic worker bypasses the staleness check since its job
is to refresh weather data on schedule."
```

---

### Task 5: Update WeatherDetailViewModel to use forceRefresh

**Objective:** User-initiated refresh from the detail screen should always force a network call.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailViewModel.kt`
- Modify: `app/src/test/java/com/clockweather/app/presentation/detail/WeatherDetailViewModelTest.kt`

**Step 1: Update refreshWeatherAndWidgets**

```kotlin
// In WeatherDetailViewModel.kt:
private suspend fun refreshWeatherAndWidgets(
    location: Location,
    forecastDays: Int
) {
    refreshWeatherUseCase(location, forecastDays = forecastDays, forceRefresh = true)
    val app = context.applicationContext as? ClockWeatherApplication
    app?.refreshAllWidgets(app)
}
```

**Step 2: Update RefreshWeatherUseCase**

```kotlin
// In RefreshWeatherUseCase.kt:
class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend operator fun invoke(
        location: Location,
        forecastDays: Int = 7,
        forceRefresh: Boolean = false
    ) = weatherRepository.refreshWeatherData(location, forecastDays, forceRefresh)
}
```

**Step 3: Update tests**

```kotlin
// Verify detail screen calls refreshWeatherData with forceRefresh = true
```

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: detail screen refresh forces network call

User-initiated refresh from the detail screen bypasses the staleness
check to ensure fresh data is always fetched."
```

---

### Task 6: Update SettingsViewModel to use forceRefresh

**Objective:** Provider change should force a refresh to fetch data from the new provider.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/presentation/settings/SettingsViewModel.kt`

**Step 1: Update refreshWeatherForProviderChange**

```kotlin
// In SettingsViewModel.kt:
private suspend fun refreshWeatherForProviderChange(
    locationRepository: LocationRepository,
    weatherRepository: WeatherRepository,
    forecastDays: Int
) {
    val locations = locationRepository.getSavedLocations().first()
    val location = locations.firstOrNull() ?: return
    weatherRepository.refreshWeatherData(
        location = location,
        forecastDays = forecastDays,
        forceRefresh = true  // Force refresh when provider changes
    )
}
```

**Step 2: Remove scheduleImmediateRefresh call**

```kotlin
// In setWeatherProvider(), remove this line:
// com.clockweather.app.worker.WeatherUpdateScheduler.scheduleImmediateRefresh(context)
// The refreshWeatherForProviderChange already does the refresh
```

**Step 3: Commit**

```bash
git add -A
git commit -m "feat: provider change forces refresh

When the user changes the weather provider, force a refresh to fetch
data from the new provider. Remove redundant scheduleImmediateRefresh
call since the refresh is already done inline."
```

---

### Task 7: Remove shouldRefreshWeather tests

**Objective:** Clean up tests for removed functionality.

**Files:**
- Modify: `app/src/test/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdaterTest.kt`

**Step 1: Remove tests for shouldRefreshWeather**

```kotlin
// Remove these tests:
// - shouldRefreshWeather returns true when weather is null
// - shouldRefreshWeather returns true when lastUpdated is older than 30 minutes
// - shouldRefreshWeather returns false when lastUpdated is recent
// - shouldRefreshWeather returns true when daily forecast is before today
// - shouldRefreshWeather returns true when future forecast days are insufficient
```

**Step 2: Update remaining tests**

```kotlin
// Update tests to verify widgets don't make network calls
// Mock weatherRepository.getWeatherData() to return cached data
// Verify refreshWeatherData() is NOT called
```

**Step 3: Run all tests**

```bash
./gradlew testDebugUnitTest
```

**Step 4: Commit**

```bash
git add -A
git commit -m "test: remove tests for removed shouldRefreshWeather

Clean up tests for functionality that was moved to the repository layer."
```

---

### Task 8: Update Google provider to include hourly in widget fetch

**Objective:** Since we're removing `fetchWidgetWeatherData()`, ensure `fetchWeatherData()` always includes hourly data for all providers.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/data/provider/GoogleWeatherProvider.kt`

**Step 1: Verify fetchWeatherData includes hourly**

```kotlin
// GoogleWeatherProvider.fetchWeatherData() already includes hourly
// No changes needed — just verify the implementation
```

**Step 2: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.clockweather.app.data.provider.GoogleWeatherProviderTest"
```

**Step 3: Commit (if any changes)**

```bash
git add -A
git commit -m "fix: ensure Google provider includes hourly data

Verify fetchWeatherData() includes hourly forecast for consistent
behavior across all providers."
```

---

### Task 9: Integration test — verify consistent data

**Objective:** Write an integration test that verifies widget and detail screen show the same data.

**Files:**
- Create: `app/src/test/java/com/clockweather/app/data/repository/WeatherRepositoryIntegrationTest.kt`

**Step 1: Write integration test**

```kotlin
@Test
fun `widget and detail screen read same data from DB`() = runTest {
    // Setup: mock provider returns weather with hourly data
    // Action: call refreshWeatherData()
    // Verify: getWeatherData() returns data with hourly forecasts
    // Verify: hourly forecasts are not empty
}

@Test
fun `multiple refreshes within 30 minutes only hit network once`() = runTest {
    // Setup: mock provider
    // Action: call refreshWeatherData() twice within 30 minutes
    // Verify: provider.fetchWeatherData() was called only once
}

@Test
fun `forceRefresh bypasses staleness check`() = runTest {
    // Setup: last refresh was 5 minutes ago
    // Action: call refreshWeatherData(forceRefresh = true)
    // Verify: provider.fetchWeatherData() was called
}
```

**Step 2: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.clockweather.app.data.repository.WeatherRepositoryIntegrationTest"
```

**Step 3: Commit**

```bash
git add -A
git commit -m "test: add integration tests for weather refresh architecture

Verify widget and detail screen read the same data from DB.
Verify staleness check prevents redundant network calls.
Verify forceRefresh bypasses staleness check."
```

---

### Task 10: Update documentation

**Objective:** Document the new architecture for future maintainers.

**Files:**
- Modify: `docs/ARCHITECTURE.md` (create if doesn't exist)

**Step 1: Write architecture doc**

```markdown
# Weather Data Architecture

## Overview

Weather data flows through a single refresh path:

1. **Network refresh drivers** trigger `WeatherRepository.refreshWeatherData()`
2. **Repository** checks staleness, fetches from provider if needed, persists to DB
3. **Consumers** (widgets, detail screen) read from DB via `getWeatherData()` Flow

## Network Refresh Drivers

- `WeatherUpdateWorker` — periodic refresh every 15-30 minutes
- `WeatherDetailViewModel.refresh()` — user-initiated pull-to-refresh
- `SettingsViewModel.setWeatherProvider()` — provider change

All drivers pass `forceRefresh = true` to bypass the staleness check.

## Staleness Check

The repository tracks the last refresh time in DataStore. If data is less than
30 minutes old, the repository skips the network call and returns cached data.

User-initiated refreshes and the periodic worker bypass this check with
`forceRefresh = true`.

## Widgets

Widgets **never** make network calls. They read from the DB via
`getWeatherData()` Flow. When the DB is updated, the Flow emits and
widgets automatically re-render.

## Providers

All providers implement a single `fetchWeatherData()` method that returns
complete weather data including hourly forecasts. There is no separate
"widget" fetch method.
```

**Step 2: Commit**

```bash
git add -A
git commit -m "docs: add weather data architecture documentation

Document the unified refresh path, staleness check, and widget behavior."
```

---

## Summary

| Before | After |
|--------|-------|
| 2 refresh methods (`refreshWeatherData`, `refreshWidgetWeatherData`) | 1 refresh method (`refreshWeatherData` with `forceRefresh` param) |
| Widgets make network calls if stale >30 min | Widgets never make network calls |
| `allowWeatherRefresh` parameter on `updateWidget()` | Removed — widgets just read from DB |
| `shouldRefreshWeather()` in `BaseWidgetUpdater` | Moved to repository as staleness check |
| Inconsistent provider behavior (Google skips hourly) | All providers return same data shape |
| Multiple redundant network calls | Single network call per refresh cycle |

**Expected outcome:**
- Widget and detail screen always show consistent data
- Fewer network calls (no redundant widget refreshes)
- Simpler code (one refresh path, no special cases)
- Easier to maintain (clear separation of concerns)
