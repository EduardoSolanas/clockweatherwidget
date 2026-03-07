# Clock & Weather Widget - Android App Implementation Plan

## Context

Build a native Android clock & weather widget app (clone of "Sense V2 Flip Clock & Weather") for Play Store release. The app has two main components: a home screen widget with flip clock + weather, and a detailed weather page that opens on widget tap. Uses Open-Meteo API for weather data (will need commercial license for Play Store - free tier is non-commercial only).

**Tech Stack**: Native Kotlin, MVVM + Clean Architecture, Jetpack Compose (detail page), RemoteViews (widget), Hilt, Retrofit, Room, WorkManager

---

## Phase 1: Shared Foundation (sequential, must complete first)

### Step 1.1 - Project Scaffolding
- Create Android project with package `com.clockweather.app`
- Configure Gradle with all dependencies (Compose BOM, Hilt, Retrofit+Moshi, Room, WorkManager, Play Services Location, DataStore)
- `libs.versions.toml` with AGP 8.5+, Kotlin 2.0+, Hilt 2.51+
- `ClockWeatherApplication.kt` with `@HiltAndroidApp`
- AndroidManifest: permissions (INTERNET, LOCATION, BOOT_COMPLETED, RECEIVE_BOOT_COMPLETED)
- minSdk 26, targetSdk 35

### Step 1.2 - Domain Layer (pure Kotlin)
Files in `domain/`:
- **Models**: `CurrentWeather.kt`, `DailyForecast.kt`, `HourlyForecast.kt`, `WeatherData.kt`, `Location.kt`
- **Enums**: `WeatherCondition.kt` (full WMO code mapping 0-99 to descriptions + icon resources), `WindDirection.kt` (16 compass directions from degrees), `TemperatureUnit.kt`, `SpeedUnit.kt`
- **Repository interfaces**: `WeatherRepository.kt`, `LocationRepository.kt`
- **Use cases**: `GetWeatherDataUseCase`, `GetCurrentWeatherUseCase`, `GetDailyForecastUseCase`, `GetHourlyForecastUseCase`, `SearchLocationUseCase`, `GetCurrentLocationUseCase`, `RefreshWeatherUseCase`
- **Common**: `UiState.kt` (sealed: Loading/Success/Error), `DateFormatter.kt`, `TemperatureFormatter.kt`

### Step 1.3 - Data Layer: Remote (API)
Files in `data/remote/`:
- **DTOs**: `WeatherResponseDto.kt`, `CurrentWeatherDto.kt`, `HourlyWeatherDto.kt`, `DailyWeatherDto.kt`, `GeocodingResponseDto.kt`, `GeoLocationDto.kt`
- **Retrofit interfaces**: `OpenMeteoWeatherApi.kt` (GET `/v1/forecast` with current+hourly+daily params, 16-day forecast), `OpenMeteoGeocodingApi.kt` (GET `/v1/search`)
- **Mapper**: `WeatherDtoMapper.kt` (DTO -> Domain, including hourly aggregation for daily humidity/pressure)
- **DI**: `NetworkModule.kt` (Retrofit + OkHttp + Moshi + logging interceptor)

**Key API call**: Single endpoint `https://api.open-meteo.com/v1/forecast` with `current`, `hourly`, `daily` params, `timezone=auto`, `forecast_days=16`

### Step 1.4 - Data Layer: Local (Room)
Files in `data/local/`:
- **Entities**: `CurrentWeatherEntity.kt`, `HourlyForecastEntity.kt`, `DailyForecastEntity.kt`, `LocationEntity.kt`
- **DAOs**: `CurrentWeatherDao.kt`, `HourlyForecastDao.kt`, `DailyForecastDao.kt`, `LocationDao.kt`
- **Database**: `WeatherDatabase.kt`
- **Mapper**: `WeatherEntityMapper.kt` (Entity <-> Domain)
- **DI**: `DatabaseModule.kt`

### Step 1.5 - Repository Implementations + DI
- `WeatherRepositoryImpl.kt` - **offline-first** strategy: emit cached data first, then fetch API, save to Room, emit fresh data. On network error, fall back to cache.
- `LocationRepositoryImpl.kt` - FusedLocationProvider + geocoding
- `RepositoryModule.kt`, `LocationModule.kt`, `AppModule.kt`

### Step 1.6 - Background Workers + Receivers
- `WeatherUpdateWorker.kt` - WorkManager periodic task (every 30-60 min)
- `WeatherUpdateScheduler.kt` - schedules/cancels WorkManager
- `BootCompletedReceiver.kt` - re-register alarms after reboot
- `TimeChangedReceiver.kt` - timezone change handling

### Step 1.7 - Weather Icon Assets (parallelizable with 1.2-1.6)
- ~20 vector drawable XML weather icons (clear day/night, partly cloudy day/night, cloudy, fog, drizzle, rain light/moderate/heavy, snow light/moderate/heavy, thunderstorm, hail)
- Metric icons: humidity, pressure, visibility, sunrise, sunset, UV, dew point, precipitation, wind arrow

---

## Phase 2: Parallel Tracks (start after Phase 1)

### Track 1: Clock & Weather Widget

**2A.1 - Flip Clock Renderer**
- `FlipClockRenderer.kt` - Canvas-based digit bitmap rendering
- Renders each digit (0-9) as a Bitmap: dark rounded rect background, large numeral in custom font, horizontal split line with shadow
- Digit cache (SparseArray) to avoid re-rendering unchanged digits
- `renderTime(hour, minute, is24Hour)` -> `FlipClockBitmaps` data class
- Custom `.ttf` font asset for clock digits

**2A.2 - Widget Layouts (XML)**
- `widget_compact.xml` (Variant A ~4x2 cells): flip clock digits as ImageViews + weather icon + date/city/condition/temp TextViews
- `widget_extended.xml` (Variant B ~4x3 cells): same as compact + 5-day forecast row at bottom (day name, date, icon, precip%, high/low per day)
- `widget_background.xml` - semi-transparent rounded rectangle

**2A.3 - Widget Providers**
- `CompactWidgetProvider.kt` / `ExtendedWidgetProvider.kt` - AppWidgetProvider subclasses
- `CompactWidgetUpdater.kt` / `ExtendedWidgetUpdater.kt` - builds RemoteViews, sets digit bitmaps, weather data, click PendingIntent to WeatherDetailActivity
- `WidgetDataBinder.kt` - shared data binding logic
- `WeatherIconMapper.kt` - WMO code -> drawable resource
- Hilt access via `EntryPointAccessors` (BroadcastReceiver can't use @AndroidEntryPoint)

**2A.4 - Widget Update Scheduling**
- `WidgetUpdateScheduler.kt` - AlarmManager repeating at 60s for clock tick
- Integration with WorkManager weather updates -> broadcast to widget providers
- `USE_EXACT_ALARM` permission for clock accuracy on Android 12+

**2A.5 - Widget Configuration Activity**
- `WidgetConfigActivity.kt` / `WidgetConfigViewModel.kt` / `WidgetConfigScreen.kt` (Compose)
- City picker (search + GPS auto-detect), unit selector (C/F, km/h/mph)
- Save config to SharedPreferences keyed by widget ID

**2A.6 - Provider Info XML + Manifest**
- `widget_compact_info.xml` / `widget_extended_info.xml` - AppWidgetProviderInfo
- AndroidManifest: widget receivers, config activity, preview images

### Track 2: Weather Detail Page

**2B.1 - Theme + Compose Setup**
- `Theme.kt`, `Color.kt`, `Typography.kt`, `Shape.kt` - Material 3 theme with blue gradient weather aesthetic
- `WeatherDetailActivity.kt` - @AndroidEntryPoint Compose host

**2B.2 - Current Weather Card**
- `CurrentWeatherCard.kt` - Large temperature display with high/low arrows, weather condition text + icon
- `WeatherMetricRow.kt` - Reusable: icon + label + value (humidity, feels like, dew point, precipitation, wind, visibility, pressure)
- `WindDirectionIndicator.kt` - Rotatable arrow composable
- "Latest update: X minutes ago" footer

**2B.3 - Daily Forecast UI**
- `DailyForecastStrip.kt` - Horizontal 6-day strip (day, date, icon, precip%, high/low). Weekend days highlighted in yellow
- `DailyForecastDetailList.kt` - Scrollable 10-day detailed forecast
- `DailyForecastDetailItem.kt` - Per day: condition description, precip % + amount (in), pressure (mBar), humidity %, wind speed+direction with arrow, sunrise/sunset times, duration of day

**2B.4 - Hourly Forecast UI**
- `HourlyForecastList.kt` - Scrollable 72-hour list
- `HourlyForecastItem.kt` - Per hour: day+time, weather alert icon, condition, precip%, humidity%, dew point, UV index, temperature + icon, pressure, wind speed+direction

**2B.5 - Main Screen + ViewModel**
- `WeatherDetailScreen.kt` - Main scaffold with scrollable content (current card -> daily strip -> daily detail -> hourly detail)
- `WeatherDetailViewModel.kt` - StateFlow collecting WeatherData from repository
- City name in top bar with back arrow, pull-to-refresh

**2B.6 - Settings Screen**
- `SettingsActivity.kt` / `SettingsViewModel.kt` / `SettingsScreen.kt`
- Temperature unit (C/F), wind speed unit, update frequency, manage locations

---

## Phase 3: Integration & Polish (sequential, after Phase 2)

1. **Integration testing**: Widget tap -> Detail Activity, data flow API -> Room -> UI, location permission flow, WorkManager -> Widget update chain
2. **Edge cases**: No network (cached data), location denied (manual city), API errors, multiple widget instances with different cities
3. **Visual polish**: Dark/light theme, widget transparency, loading/error states, smooth transitions
4. **Play Store prep**: Open-Meteo commercial API key, ProGuard/R8 rules, app icon, widget preview images, privacy policy

---

## Key Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Widget rendering | RemoteViews (not Glance) | Glance doesn't support custom fonts or Canvas rendering needed for flip clock |
| Detail page UI | Jetpack Compose | Modern, declarative, no RemoteViews constraints |
| Data caching | Room (offline-first) | Show cached data immediately, refresh in background |
| Clock updates | AlarmManager 60s repeating | `updatePeriodMillis` minimum is 30 min, too slow for clock |
| Weather updates | WorkManager periodic 30min | Battery-efficient background fetch |
| DI in widget | EntryPointAccessors | AppWidgetProvider extends BroadcastReceiver, can't use @AndroidEntryPoint |
| Weather API | Open-Meteo | No API key needed for dev, all required data fields, commercial license available |

---

## Critical Files

| File | Why Critical |
|------|-------------|
| `data/repository/WeatherRepositoryImpl.kt` | Central data orchestration, offline-first flow |
| `presentation/widget/common/FlipClockRenderer.kt` | Canvas-based digit rendering, most technically complex |
| `presentation/widget/compact/CompactWidgetProvider.kt` | Widget lifecycle, AlarmManager, Hilt entry point |
| `data/remote/api/OpenMeteoWeatherApi.kt` | API contract, query params, base URL |
| `domain/model/WeatherCondition.kt` | WMO code mapping used by both widget and detail |

---

## Agent Execution Strategy

**Model assignment**: Haiku for boilerplate, Sonnet for complex logic.

| Agent | Model | Task | Isolation |
|-------|-------|------|-----------|
| Foundation-1 | haiku | Step 1.1 scaffolding + Gradle + Manifest | main branch |
| Foundation-2 | haiku | Step 1.2 domain models + enums + interfaces | after 1.1 |
| Foundation-3 | haiku | Step 1.3 DTOs + Retrofit interfaces | after 1.2 |
| Foundation-4 | haiku | Step 1.4 Room entities + DAOs + database | after 1.2 |
| Foundation-5 | sonnet | Step 1.5 Repository impls (offline-first logic) + DI modules | after 1.3+1.4 |
| Foundation-6 | haiku | Step 1.6 Workers + receivers | after 1.5 |
| Foundation-7 | haiku | Step 1.7 Weather icon vector drawables | parallel with 1.2-1.6 |
| **Track 1** | **sonnet** | Widget: FlipClockRenderer, providers, updaters, config, layouts | worktree, after Phase 1 |
| **Track 2** | **sonnet** | Detail page: all Compose screens, ViewModel, theme | worktree, after Phase 1 |
| Integration | sonnet | Phase 3: connect tracks, test, polish | after Track 1+2 merge |

**Phase 1** runs mostly sequentially (dependencies between steps) with haiku agents for fast boilerplate generation. Steps 1.3 and 1.4 can run in parallel (both depend on 1.2 but not on each other). Step 1.7 (icons) runs fully in parallel.

**Phase 2** launches 2 sonnet agents simultaneously in isolated worktrees for Track 1 (Widget) and Track 2 (Detail Page).

---

## Verification

1. **Widget**: Add both widget variants to home screen, verify clock updates every minute, weather data displays correctly, tap opens detail page
2. **Detail page**: Verify all sections render (current, daily strip, daily detail 10-day, hourly 72h), pull-to-refresh works, data matches API
3. **Offline**: Enable airplane mode, verify cached data shown with "last updated" time
4. **Multi-location**: Configure two widget instances with different cities, verify independent data
5. **Boot**: Reboot device, verify widget resumes updates
6. **Build**: `./gradlew assembleRelease` produces signed APK/AAB
