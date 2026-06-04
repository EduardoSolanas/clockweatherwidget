# Weather Refresh Architecture Overhaul Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Make the database the single source of truth for weather state so widgets and the detail screen always render the same data while avoiding redundant network refreshes.

**Architecture:** Network refresh is centralized in `WeatherRepository`. UI consumers read from Room. Widgets never call provider/API refresh directly; they render cached DB data and may enqueue one unique refresh work request if the cache is missing or stale. Repository exposes explicit `ensureFreshWeatherData()` and `forceRefreshWeatherData()` methods instead of a Boolean flag.

**Tech Stack:** Kotlin, Room, Hilt, WorkManager, DataStore, JUnit/Robolectric

---

## Diagnosis

The current bug class comes from writing incomplete weather snapshots into the shared DB.

Current shape:

```text
WeatherRepository
├── refreshWeatherData()       full data: current + hourly + daily
└── refreshWidgetWeatherData() partial/lightweight data
```

If a widget refresh writes partial data, the detail screen reads that same DB row and loses hourly forecasts. That is how widget/detail disagreement happens.

## Non-negotiable invariants

1. **Any write to shared weather DB must persist a complete `WeatherData` shape:** current + enough hourly data for current-hour/24h graph + enough daily data for widget/detail forecast.
2. **Widgets must not call weather providers directly.** They repaint from DB only.
3. **Widgets may enqueue a unique one-shot refresh if cache is missing/stale.** This preserves first-install UX without N widgets making N network calls.
4. **Detail initial load should not always force network.** It should ensure freshness.
5. **User-initiated pull refresh and provider changes should force network.**
6. **RemoteViews do not observe Room flows.** After DB refresh, widgets must be manually repainted with `refreshAllWidgets()`.

---

## Target API

### Repository

```kotlin
interface WeatherRepository {
    fun getWeatherData(location: Location): Flow<WeatherData?>

    suspend fun ensureFreshWeatherData(
        location: Location,
        forecastDays: Int = 7
    )

    suspend fun forceRefreshWeatherData(
        location: Location,
        forecastDays: Int = 7
    )
}
```

### Freshness decision

Use DB content, not a global DataStore timestamp.

A weather record is stale/incomplete when any of these is true:

- `weather == null`
- `currentWeather.lastUpdated` is older than the refresh interval threshold
- `currentWeather.lastUpdated` is before the local weather day
- no hourly forecast exists for the current hour
- fewer than 24 hourly forecasts exist from current hour onward
- first daily forecast is before today
- daily forecast does not cover required forecast days

This must be implemented as a pure function and tested with real `WeatherData` objects.

```kotlin
internal fun isWeatherDataFresh(
    weather: WeatherData?,
    referenceDateTime: LocalDateTime,
    requiredForecastDays: Int,
    maxAgeMinutes: Long = 30
): Boolean
```

---

## Target flow

```text
Periodic worker
→ forceRefreshWeatherData()
→ DB write
→ refreshAllWidgets() repaint only

Detail initial load
→ ensureFreshWeatherData()
→ observe DB Flow
→ refreshAllWidgets() repaint only if refresh happened

Detail pull-to-refresh
→ forceRefreshWeatherData()
→ DB write
→ refreshAllWidgets() repaint only

Settings provider change
→ forceRefreshWeatherData()
→ DB write
→ refreshAllWidgets() repaint only

Widget update
→ read DB
→ render cached/fallback
→ if missing/stale, enqueue unique one-shot WeatherUpdateWorker
→ no provider/API call from widget path
```

---

## Task 1: Extract DB-based freshness decision

**Objective:** Move refresh decisions out of `BaseWidgetUpdater` into a pure, testable domain/data helper.

**Files:**
- Create: `app/src/main/java/com/clockweather/app/domain/model/WeatherFreshness.kt`
- Create: `app/src/test/java/com/clockweather/app/domain/model/WeatherFreshnessTest.kt`
- Read only: `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`

**Step 1: Write failing tests first**

Create `WeatherFreshnessTest.kt` with real `WeatherData`, `CurrentWeather`, `HourlyForecast`, and `DailyForecast` objects. No mocks.

Required tests:

```kotlin
@Test
fun `weather is not fresh when null`()

@Test
fun `weather is fresh when current hourly and daily coverage are sufficient`()

@Test
fun `weather is not fresh when current weather is older than max age`()

@Test
fun `weather is not fresh when current hour forecast is missing`()

@Test
fun `weather is not fresh when next 24 hourly forecasts are incomplete`()

@Test
fun `weather is not fresh when daily forecasts do not cover requested days`()
```

Run RED:

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.domain.model.WeatherFreshnessTest"
```

Expected: fails because `isWeatherDataFresh` does not exist.

**Step 2: Implement minimum code**

Create `WeatherFreshness.kt`:

```kotlin
package com.clockweather.app.domain.model

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal fun isWeatherDataFresh(
    weather: WeatherData?,
    referenceDateTime: LocalDateTime,
    requiredForecastDays: Int,
    maxAgeMinutes: Long = 30
): Boolean {
    if (weather == null) return false

    val referenceHour = referenceDateTime.truncatedTo(ChronoUnit.HOURS)
    val today = referenceDateTime.toLocalDate()

    if (weather.currentWeather.lastUpdated.isBefore(referenceDateTime.minusMinutes(maxAgeMinutes))) return false
    if (weather.currentWeather.lastUpdated.toLocalDate().isBefore(today)) return false

    val futureHours = weather.hourlyForecasts
        .asSequence()
        .filter { !it.dateTime.truncatedTo(ChronoUnit.HOURS).isBefore(referenceHour) }
        .sortedBy { it.dateTime }
        .toList()

    if (futureHours.firstOrNull()?.dateTime?.truncatedTo(ChronoUnit.HOURS) != referenceHour) return false
    if (futureHours.size < 24) return false

    val futureDays = weather.dailyForecasts
        .filter { !it.date.isBefore(today) }
        .distinctBy { it.date }
        .count()

    return futureDays >= requiredForecastDays.coerceAtLeast(1)
}
```

**Step 3: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.domain.model.WeatherFreshnessTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/clockweather/app/domain/model/WeatherFreshness.kt \
        app/src/test/java/com/clockweather/app/domain/model/WeatherFreshnessTest.kt
git commit -m "feat: add DB-based weather freshness decision"
```

---

## Task 2: Replace repository refresh API with ensure/force methods

**Objective:** Make refresh intent explicit and avoid Boolean ambiguity.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/domain/repository/WeatherRepository.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt`
- Modify: `app/src/main/java/com/clockweather/app/domain/usecase/RefreshWeatherUseCase.kt`
- Test: `app/src/test/java/com/clockweather/app/data/repository/WeatherRepositoryRefreshTest.kt`

**Step 1: Write failing repository tests**

Use real in-memory Room DB and real `WeatherEntityMapper` where practical. Avoid asserting mock call counts as the main behavior. Assert persisted DB state and freshness behavior.

Required tests:

```kotlin
@Test
fun `ensureFreshWeatherData does not replace fresh cached data`()

@Test
fun `ensureFreshWeatherData refreshes missing cached data`()

@Test
fun `ensureFreshWeatherData refreshes incomplete hourly data`()

@Test
fun `forceRefreshWeatherData replaces fresh cached data`()
```

Run RED:

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.data.repository.WeatherRepositoryRefreshTest"
```

Expected: fails because API does not exist.

**Step 2: Update repository interface**

```kotlin
interface WeatherRepository {
    fun getWeatherData(location: Location): Flow<WeatherData?>

    suspend fun ensureFreshWeatherData(location: Location, forecastDays: Int = 7)

    suspend fun forceRefreshWeatherData(location: Location, forecastDays: Int = 7)
}
```

**Step 3: Implement in repository**

In `WeatherRepositoryImpl`:

```kotlin
override suspend fun ensureFreshWeatherData(location: Location, forecastDays: Int) {
    refreshMutex.withLock {
        val cached = getWeatherData(location).first()
        val now = cached?.locationReferenceDateTime() ?: java.time.LocalDateTime.now()
        if (isWeatherDataFresh(cached, now, forecastDays)) return
        refreshAndPersist(location, forecastDays)
    }
}

override suspend fun forceRefreshWeatherData(location: Location, forecastDays: Int) {
    refreshMutex.withLock {
        refreshAndPersist(location, forecastDays)
    }
}

private suspend fun refreshAndPersist(location: Location, forecastDays: Int) {
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
}
```

**Step 4: Update use case**

Prefer explicit methods:

```kotlin
class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend fun ensureFresh(location: Location, forecastDays: Int = 7) =
        weatherRepository.ensureFreshWeatherData(location, forecastDays)

    suspend fun forceRefresh(location: Location, forecastDays: Int = 7) =
        weatherRepository.forceRefreshWeatherData(location, forecastDays)
}
```

**Step 5: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.data.repository.WeatherRepositoryRefreshTest"
```

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor: split weather refresh into ensure and force paths"
```

---

## Task 3: Remove widget-specific provider/repository refresh path

**Objective:** Prevent partial weather snapshots from being written to the shared DB.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/domain/repository/WeatherRepository.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/WeatherDataProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/OpenMeteoWeatherProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/OpenWeatherMapProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/data/provider/GoogleWeatherProvider.kt`
- Modify tests under `app/src/test/java/com/clockweather/app/data/provider/`

**Step 1: Write/adjust failing tests**

Add provider tests proving `fetchWeatherData()` returns hourly data for each provider mapper/provider path where feasible:

```kotlin
@Test
fun `fetchWeatherData includes hourly forecasts`()
```

Remove tests that specify widget-lightweight fetch behavior.

Run RED for affected provider tests.

**Step 2: Remove methods**

Remove from `WeatherRepository`:

```kotlin
suspend fun refreshWidgetWeatherData(location: Location)
```

Remove from `WeatherDataProvider`:

```kotlin
suspend fun fetchWidgetWeatherData(location: Location): WeatherData
```

Remove overrides from:

- `OpenMeteoWeatherProvider`
- `OpenWeatherMapProvider`
- `GoogleWeatherProvider`

**Step 3: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest
```

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove widget-specific weather refresh path"
```

---

## Task 4: Make widgets DB-only and enqueue refresh work when needed

**Objective:** Widgets render from DB and never call `WeatherRepository` refresh methods directly.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`
- Modify: `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetProvider.kt`
- Modify: `app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt`
- Modify: `app/src/main/java/com/clockweather/app/worker/WeatherUpdateScheduler.kt`
- Test: `app/src/test/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdaterTest.kt`

**Step 1: Add unique immediate refresh scheduling**

Change `WeatherUpdateScheduler.scheduleImmediateRefresh()` to enqueue unique work, not unlimited duplicates:

```kotlin
fun scheduleImmediateRefresh(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "weather_update_immediate_work",
        ExistingWorkPolicy.KEEP,
        workRequest
    )
}
```

**Step 2: Update widget updater**

In `BaseWidgetUpdater.updateWidget()`:

- Remove `allowWeatherRefresh`
- Remove direct calls to `refreshWidgetWeatherData()` / refresh methods
- Read cached DB weather
- If missing or stale, enqueue unique immediate refresh
- Render cached/fallback immediately

Sketch:

```kotlin
val weather = weatherRepo.getWeatherData(location).first()
val referenceDateTime = weather?.locationReferenceDateTime() ?: java.time.LocalDateTime.now()
if (!isWeatherDataFresh(weather, referenceDateTime, requiredForecastDaysForRefresh(7, minimumFutureForecastDaysRequired))) {
    WeatherUpdateScheduler.scheduleImmediateRefresh(context)
}

if (weather != null) {
    WidgetDataBinder.bindWeatherViews(context, views, weather, tempUnit, weatherIconStyle)
    bindExtra(views, weather, tempUnit, prefs)
} else {
    WidgetDataBinder.bindWeatherUnavailableViews(context, views, weatherIconStyle)
}
```

**Step 3: Update callers**

Remove `allowWeatherRefresh = false` from:

- `BaseWidgetProvider.onAppWidgetOptionsChanged()`
- `ClockWeatherApplication.refreshAllWidgets()`
- tests

**Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.presentation.widget.common.BaseWidgetUpdaterTest"
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: make widget updates DB-only"
```

---

## Task 5: Update detail screen refresh behavior

**Objective:** Initial detail load ensures freshness; pull-to-refresh forces freshness.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailViewModel.kt`
- Test: `app/src/test/java/com/clockweather/app/presentation/detail/WeatherDetailViewModelTest.kt`

**Step 1: Write failing tests**

Required behavior:

```kotlin
@Test
fun `initial load ensures fresh weather without forcing refresh`()

@Test
fun `manual refresh forces weather refresh`()

@Test
fun `refresh repaints widgets after repository refresh`()
```

**Step 2: Update ViewModel**

- `loadWeather()` should call `refreshWeatherUseCase.ensureFresh(...)`
- `refresh()` should call `refreshWeatherUseCase.forceRefresh(...)`
- Keep `refreshAllWidgets()` after refresh so RemoteViews repaint from DB

**Step 3: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.presentation.detail.WeatherDetailViewModelTest"
```

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: separate detail ensure-fresh and force-refresh paths"
```

---

## Task 6: Update worker behavior

**Objective:** Periodic worker is the authoritative scheduled network refresh and repaints widgets afterward.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt`
- Test: worker tests if present; otherwise add focused tests only if project has WorkManager test infrastructure

**Step 1: Update worker**

```kotlin
locations.forEach { location ->
    weatherRepository.forceRefreshWeatherData(location, forecastDays)
}

val app = applicationContext as? ClockWeatherApplication
app?.refreshAllWidgets(applicationContext)
```

**Step 2: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: make worker force weather refreshes"
```

---

## Task 7: Update settings provider-change behavior

**Objective:** Provider changes force exactly one refresh and one widget repaint.

**Files:**
- Modify: `app/src/main/java/com/clockweather/app/presentation/settings/SettingsViewModel.kt`
- Test: existing settings tests if present

**Step 1: Update provider change refresh**

- Use `forceRefreshWeatherData()` / `RefreshWeatherUseCase.forceRefresh()` for provider changes
- Remove redundant `WeatherUpdateScheduler.scheduleImmediateRefresh(context)` after inline provider-change refresh
- Keep `triggerWidgetUpdate()` / `refreshAllWidgets()` repaint

**Step 2: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.clockweather.app.presentation.settings.*"
```

If Gradle test filtering is unreliable in this project, run:

```bash
./gradlew :app:testDebugUnitTest
```

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: make provider changes force one weather refresh"
```

---

## Task 8: Update architecture docs

**Objective:** Document the real data flow and prevent future partial DB writes.

**Files:**
- Create or modify: `docs/ARCHITECTURE.md`

**Required content:**

```markdown
# Weather Data Architecture

## Source of truth

Room is the source of truth for UI weather rendering.

## Refresh paths

- `ensureFreshWeatherData()` refreshes only when DB data is missing/stale/incomplete.
- `forceRefreshWeatherData()` always fetches from the selected provider.

## Widgets

Widgets never call weather providers directly. They render cached DB data. If the cache is missing or stale, they enqueue unique one-shot weather work and render fallback/cached data until the worker completes.

RemoteViews do not observe Room. After a DB refresh, callers must invoke `refreshAllWidgets()` to repaint widgets.

## Provider contract

Every provider refresh persisted to shared DB must include complete weather data: current, hourly, and daily forecasts. No lightweight partial provider writes are allowed in the shared weather tables.
```

**Step 2: Commit**

```bash
git add docs/ARCHITECTURE.md
git commit -m "docs: document weather refresh architecture"
```

---

## Verification checklist

Run before declaring done:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Manual smoke test on device/emulator:

1. Select OpenWeatherMap provider.
2. Force refresh from detail screen.
3. Confirm detail hero condition, hourly current-hour condition, and widget condition match.
4. Confirm hourly graph shows 24h.
5. Trigger widget update/resizing.
6. Confirm no direct widget network refresh happens; widget repaints cached DB state.
7. Clear app data / first install path.
8. Add widget with empty DB.
9. Confirm widget renders fallback and schedules one unique refresh.
10. After worker completes, confirm widget and detail screen show same condition.

---

## Expected outcome

- Widget and detail screen render the same weather snapshot.
- No widget path writes partial weather data to DB.
- Initial detail screen open avoids unnecessary API calls when DB is fresh.
- Pull-to-refresh and provider changes still force fresh data.
- Empty-cache widgets recover through unique WorkManager refresh, not direct API calls.
