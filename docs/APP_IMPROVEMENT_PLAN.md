# ClockWeatherWidget App Improvement Plan

Audit date: 2026-06-01

## Scope

This plan is based on a static audit of the Android app, including the current working-tree changes for WeatherAPI removal, widget text-scale simplification, and the widget current-day high/low fix.

The goal is not a rewrite. Each item should be handled as a small, test-first change with narrow verification before broader regression checks.

## Phase 0 - Stabilize Current Change Set

### 0.1 Verify the current branch after the active cleanup work

Why:
- The working tree currently contains provider removal, widget text-scale simplification, and widget forecast/card fixes.
- KSP/Gradle generated state has already caused stale-cache failures during targeted test runs.

Files:
- Current working tree

Actions:
- Run `.\gradlew.bat clean`.
- Run `.\gradlew.bat :app:testDebugUnitTest --no-daemon`.
- Run `.\gradlew.bat :app:lintDebug --no-daemon`.
- Review any failures before starting broader simplification.

Acceptance:
- Unit tests pass.
- Lint output is understood, or new actionable issues are documented.

## Phase 1 - Correctness And User-Visible Behavior

### 1.1 Fix weather refresh interval preference mismatch

Why:
- Settings UI writes `weather_refresh_interval_minutes`.
- Startup and boot scheduling still read `update_interval_minutes`.
- This means the visible refresh interval setting can be ignored by scheduled background work.

Files:
- `app/src/main/java/com/clockweather/app/presentation/settings/SettingsViewModel.kt`
- `app/src/main/java/com/clockweather/app/worker/WeatherUpdateScheduler.kt`
- `app/src/main/java/com/clockweather/app/receiver/BootCompletedReceiver.kt`
- `app/src/test/java/com/clockweather/app/worker/WeatherUpdateSchedulerOnCreateTest.kt`

Actions:
- Make `KEY_WEATHER_REFRESH_INTERVAL` the single preference key for periodic weather refresh.
- Remove stale `KEY_UPDATE_INTERVAL`, `updateIntervalMinutes`, and `setUpdateInterval()` if still unused.
- Reschedule WorkManager when `setWeatherRefreshInterval()` changes the value.
- Update boot/startup scheduler tests to use the active key.

Acceptance:
- Changing the setting persists the active key and calls scheduler with the clamped value.
- App startup and boot use the same key.
- `WeatherUpdateSchedulerOnCreateTest` passes.

### 1.2 Keep debug condition cycling

Why:
- `CurrentWeatherCard.kt` lets a tap cycle through all weather conditions via `debugIndex`.
- The debug cycle is intentionally retained by request so weather icon/background states remain easy to inspect.

Files:
- `app/src/main/java/com/clockweather/app/presentation/detail/screen/CurrentWeatherCard.kt`
- `docs/PLAN.md`
- `docs/TASKS_improvements_v2.md`

Actions:
- Keep the current debug cycle behavior.
- Do not remove or gate it while it is still being used for visual checks.

Acceptance:
- Tapping the hero card still cycles weather conditions.
- Existing weather detail screen tests still pass.

### 1.3 Consolidate provider and forecast-day refresh ownership

Why:
- `SettingsViewModel.setWeatherProvider()` refreshes weather.
- `WeatherDetailViewModel` also observes provider and forecast-day changes and refreshes.
- Provider/forecast changes can fan out into duplicated refresh paths.

Files:
- `app/src/main/java/com/clockweather/app/presentation/settings/SettingsViewModel.kt`
- `app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailViewModel.kt`
- `app/src/test/java/com/clockweather/app/presentation/detail/WeatherDetailViewModelTest.kt`
- `app/src/test/java/com/clockweather/app/presentation/settings/SettingsProviderChangeRefreshTest.kt`

Actions:
- Choose one owner for network refresh side effects.
- Keep view models responsible for observing repository state, not duplicating refresh triggers, where practical.
- Add or adjust tests so provider changes cause one intended refresh path.

Acceptance:
- Provider switch refreshes weather once.
- Forecast-day change refreshes weather once with the selected range.
- Detail screen does not keep stale data after a provider change.

### 1.4 Handle silent refresh failures visibly enough to debug

Why:
- `WeatherDetailViewModel.refresh()` catches `Exception` and ignores it.
- Several widget paths catch and suppress exceptions.
- Silent failures make broken weather updates look like stale UI.

Files:
- `app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailViewModel.kt`
- `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`
- `app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt`

Actions:
- Preserve graceful fallback behavior, but log or expose failure state where the user can retry.
- Keep cancellation handling explicit.
- Avoid logging secrets or precise location data.

Acceptance:
- Manual refresh failure leaves a user-visible retry/error state or clear diagnostic log.
- Widget update failure still keeps fallback RemoteViews.

## Phase 2 - Low-Risk Dead Code Removal

### 2.1 Remove unused domain use-case wrappers

Why:
- These wrappers have no call sites in main or test code:
  - `GetCurrentWeatherUseCase`
  - `GetDailyForecastUseCase`
  - `GetHourlyForecastUseCase`
- The app uses `GetWeatherDataUseCase` directly where needed.

Files:
- `app/src/main/java/com/clockweather/app/domain/usecase/GetCurrentWeatherUseCase.kt`
- `app/src/main/java/com/clockweather/app/domain/usecase/GetDailyForecastUseCase.kt`
- `app/src/main/java/com/clockweather/app/domain/usecase/GetHourlyForecastUseCase.kt`

Actions:
- Delete the unused wrappers.
- Run compile/unit tests to catch accidental Hilt references.

Acceptance:
- No references remain.
- Unit tests compile and pass.

### 2.2 Remove old unused forecast/list composables

Why:
- Static search shows these composables are not called by the active detail screen:
  - `DailyForecastStrip`
  - `DailyForecastDetailList`
  - `HourlyForecastList`
- They preserve older UI paths and duplicate concepts from the active cards/graph.

Files:
- `app/src/main/java/com/clockweather/app/presentation/detail/screen/DailyForecastStrip.kt`
- `app/src/main/java/com/clockweather/app/presentation/detail/screen/DailyForecastDetailList.kt`
- `app/src/main/java/com/clockweather/app/presentation/detail/screen/HourlyForecastList.kt`
- `app/src/main/java/com/clockweather/app/presentation/detail/screen/CurrentWeatherCard.kt`

Actions:
- Delete unused composable files after confirming no preview/test references.
- Remove `WeatherMetricRow` and `WindDirectionIndicator` if they become unused.

Acceptance:
- No unused composable references remain.
- Weather detail screen tests compile and pass.

### 2.3 Remove unused widget id plumbing

Why:
- `WidgetConfigViewModel.widgetId` is assigned but never read.
- `WeatherDetailActivity.EXTRA_WIDGET_ID` is written by `WidgetDataBinder.buildDetailPendingIntent()` but not consumed.

Files:
- `app/src/main/java/com/clockweather/app/presentation/widget/configuration/WidgetConfigActivity.kt`
- `app/src/main/java/com/clockweather/app/presentation/widget/configuration/WidgetConfigViewModel.kt`
- `app/src/main/java/com/clockweather/app/presentation/widget/common/WidgetDataBinder.kt`
- `app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailActivity.kt`

Actions:
- Remove `setWidgetId()` and the backing field if the config flow does not use it.
- Remove the unused detail intent extra and constant.

Acceptance:
- Widget configuration still completes and returns `EXTRA_APPWIDGET_ID` through the activity result.
- Detail activity still opens from widget taps.

### 2.4 Remove unused foreground service permission

Why:
- Manifest declares `android.permission.FOREGROUND_SERVICE`.
- Static search found no `startForeground` usage.

Files:
- `app/src/main/AndroidManifest.xml`

Actions:
- Remove the permission if no foreground service is planned.

Acceptance:
- Manifest remains valid.
- App install and background worker behavior are unchanged.

## Phase 3 - Reliability, Privacy, And Battery

### 3.1 Reduce location debug logging

Why:
- `LocationRepositoryImpl` logs location resolution details, including coordinates and names.
- This is useful during development but noisy and privacy-sensitive in production logs.

Files:
- `app/src/main/java/com/clockweather/app/data/repository/LocationRepositoryImpl.kt`

Actions:
- Gate detailed geo logs behind `BuildConfig.DEBUG`, or reduce to coarse event logs.
- Avoid logging precise latitude/longitude in production.

Acceptance:
- Debug builds still provide enough troubleshooting signal.
- Release builds do not emit precise location diagnostics.

### 3.2 Make widget update failure states easier to inspect

Why:
- Widget code intentionally catches many exceptions to avoid "Can't load widget".
- That is correct for user experience, but failures can become hard to diagnose.

Files:
- `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetProvider.kt`
- `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`

Actions:
- Keep fallback RemoteViews behavior.
- Normalize log tags/messages for update phases.
- Add narrow tests for failure paths that must keep fallback widgets visible.

Acceptance:
- Fallback still renders when update fails.
- Logs identify provider/update phase without dumping user data.

### 3.3 Re-check battery optimization prompt behavior

Why:
- The app asks users for unrestricted battery optimization.
- This is useful for widgets, but it is a high-friction permission-style flow.

Files:
- `app/src/main/java/com/clockweather/app/presentation/detail/screen/WeatherDetailScreen.kt`
- `app/src/main/java/com/clockweather/app/presentation/settings/SettingsScreen.kt`
- `app/src/main/AndroidManifest.xml`

Actions:
- Keep the prompt only where it directly improves widget reliability.
- Ensure the app still works reasonably without the exemption.
- Avoid repeatedly interrupting users.

Acceptance:
- User can dismiss/ignore setup without blocking weather detail usage.
- Widget reliability guidance remains available in settings.

## Phase 4 - Maintainability Hotspots

### 4.1 Split the largest UI files by responsibility

Why:
- The largest files are difficult to review safely:
  - `CurrentWeatherCard.kt`
  - `WeatherAnimatedIcon.kt`
  - `SettingsScreen.kt`
  - `HourlyWeatherGraph.kt`
  - `BaseWidgetUpdater.kt`

Actions:
- Do not restyle while splitting.
- Move cohesive sections into files with the same package and internal visibility.
- Prefer small extraction targets: metrics cards, forecast row, air quality card, animation backgrounds, settings sections.

Acceptance:
- No behavior changes.
- Existing tests pass before and after each extraction.

### 4.2 Clean up old test narrative comments

Why:
- Completed in the current cleanup pass.
- Prior implementation-phase comments were removed from tests while keeping the useful test names.

Files:
- `app/src/test/java/com/clockweather/app/domain/usecase/RefreshWeatherUseCaseTest.kt`
- `app/src/test/java/com/clockweather/app/presentation/settings/ForecastDaysDefaultTest.kt`
- `app/src/test/java/com/clockweather/app/presentation/detail/ForecastScrollBehaviorTest.kt`
- `app/src/test/java/com/clockweather/app/presentation/detail/screen/ForecastColumnWidthTest.kt`

Actions:
- Keep useful test names.
- Avoid reintroducing implementation-phase narrative comments.

Acceptance:
- Coverage intent remains clear.
- Test files are shorter and less historical.

### 4.3 Tighten lint gradually

Why:
- `lint` currently has `abortOnError = false`, `warningsAsErrors = false`, and a baseline.
- This is acceptable while stabilizing, but it allows regressions to accumulate.

Files:
- `app/build.gradle.kts`
- `app/lint-baseline.xml`

Actions:
- First regenerate/review the baseline after current cleanup lands.
- Then enable failing lint for a small set of high-value checks.
- Keep broad lint tightening separate from feature work.

Acceptance:
- CI catches newly introduced actionable lint issues.
- Existing baseline is understood and shrinking.

## Suggested Execution Order

1. Finish and verify the current working tree.
2. Fix refresh interval preference mismatch.
3. Keep the current debug condition cycling.
4. Remove dead use-case wrappers and unused composables.
5. Remove widget id plumbing and unused foreground service permission.
6. Consolidate provider/forecast refresh ownership.
7. Reduce production geo logging.
8. Split large UI files only after behavior is covered by tests.

## Test Commands By Area

Current branch health:
- `.\gradlew.bat clean`
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
- `.\gradlew.bat :app:lintDebug --no-daemon`

Refresh scheduling:
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.clockweather.app.worker.WeatherUpdateSchedulerOnCreateTest" --no-daemon`

Weather detail:
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.clockweather.app.presentation.detail.*" --no-daemon`

Widget behavior:
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.clockweather.app.presentation.widget.*" --no-daemon`

Settings/provider behavior:
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.clockweather.app.presentation.settings.*" --no-daemon`
