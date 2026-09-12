# Time, weather and location sync review

Original review: 5 September 2026, `feat/disable-ads-debug-builds`. Latest source/status audit: **11 September 2026**. DONE means the scoped implementation is present; the historical commit checkpoints below record earlier verification. It does not imply deployment or launcher verification.

Read the current status table and implementation order first. Each numbered finding starts with a current status; its remaining evidence/change/acceptance text preserves the original audit unless explicitly updated. Original failures are historical, not claims that every defect still exists. The implementation follow-up records earlier runs; the current verification status is recorded below.

**Latest verification status:** the toolchain is installed and Gradle runs — Microsoft OpenJDK 17.0.20.1 and the Android SDK at the `local.properties` path (platform-35, build-tools 35.0.0, licences accepted). `gradlew testDebugUnitTest lintDebug assembleDebug` passes: **409 tests across 86 classes, 0 failures, 0 errors; lint 0 errors** (96 warnings, 2 hints, all pre-existing). Historical passing counts below predate that run. No device, emulator, live-provider, Doze, battery or deployment validation was performed.

The six new sync findings are tracked as **S1-S6** in [refresh_improvements.md, section 8](../refresh_improvements.md#8-weather-sync-review--11-september-2026), which holds the scenarios, source references and acceptance checks. **S1-S3 and S5-S6 are DONE and verified by the run above; S4 alone remains OPEN by design.**

## Recommended direction

Keep Android responsible for the ticking clock. Keep Room as the persistent weather cache and WorkManager as the background weather scheduler. Improve the identity, age and visibility of cached data before increasing refresh frequency.

**DONE in code:** weather-owned city labels/coordinates, fetch-before-location-save, independent optional-section ages, Google offset parsing, configured widget forecast coverage, optional-cache rejection after significant movement, placeholder preservation and the resume freshness hook. Request-count and rendering tests also exist; their measurements below are historical.

Five of the six sync findings are now implemented and verified: hourly ownership after travel (S1), newest-fix retention (S2), accumulated movement (S3), requested optional-section freshness (S5) and duplicate startup requests (S6). Only coherent reads (S4) remain, deliberately deferred — writes are already transactional and Room's invalidation fires post-commit, so the residual skew is a frame rather than a state, and the persistent mixed generation that gave S4 most of its weight was S1. Keep renderer deduplication optional until launcher evidence justifies it.

### Original test blocker — DONE

The original release-variant assertion was corrected to branch on `BuildConfig.DEBUG`; this code correction is DONE. The checkpoint recorded 352 passing tests per variant, with a later historical entry recording 387. Neither figure is a new run; the current figure is the 409-test run recorded above.

Assumptions: retain the existing native Kotlin/Compose application, Android API 26 minimum, and the documented current-location-first product. Per-widget cities and world clocks remain a separate product decision. “Sync” here means synchronizing the app and home-screen widgets with device time, location and weather providers, rather than account or cross-device synchronization.

## What already works in the design

| Area | Current implementation | Recommendation |
| --- | --- | --- |
| Clock | `BaseWidgetUpdater.buildViews()` uses host-driven `TextClock` views. | Preserve it; weather downloads must not drive minute updates. |
| Weather storage | `WeatherRepositoryImpl` transactionally writes current/daily and replaces hourly when included in scope. | Preserve transactional writes; address retained-hour ownership and non-atomic observations (S1/S4). |
| Background refresh | `WeatherUpdateScheduler` uses unique network-constrained periodic work, exponential retry backoff, and the saved interval. Default: 30 minutes; accepted range: 15–1,440 minutes. | Preserve the scheduler and make freshness decisions consistent across callers. |
| Manual widget refresh | Separate unique expedited work with a regular-work fallback and `KEEP`. | Preserve quota fallback; unify its semantics with app refresh. |
| Wake recovery | `ScreenWakeReceiver` redraws cache, then enqueues weather work. | Keep this opportunistic recovery, with the process-lifetime limitation below. |
| Travel | Passive updates detect movement of at least 5 km; the worker resolves a location and forces refresh after significant movement. | Improve cache ownership and fix validation rather than adding constant GPS polling. |
| Partial failures | The worker continues through saved locations, redraws cached widgets, and retries failures. | Retain this behavior, while exposing age and errors to the user. |

`TextClock` supports date/time display and timezone selection. Use the device timezone for the existing local clock. A separate time server or minute-by-minute weather job is unnecessary for this product. [Android TextClock reference](https://developer.android.com/reference/android/widget/TextClock)

## Prioritized findings

The code paths below were inspected directly. Failure scenarios are deductions from those paths, not claims of reproduction on a physical phone.

Severity alone is a poor ordering signal here: the first revision of this document marked four findings P1, which is too many to prioritize by. Each finding now carries a severity and an effort estimate, and several findings have been split, because a number of them bundled a one-line bug together with an architectural redesign and were being priced as the redesign.

| # | Finding | Status | Implemented locally | Remaining work |
| --- | --- | --- | --- | --- |
| 1 | Independent AQ/pollen ages | DONE for stored ages/provider reuse | Two timestamps, migration, provider policies and unknown-age handling | Repository freshness integration S5 DONE; user-visible age is separate |
| 2 | City/weather snapshot identity | PARTIAL | Weather-owned label/coordinates; location saved after successful fetch; optional-section reuse blocked after movement; regression tests present | S1 and S3 DONE; S4 open; no single location+weather transaction |
| 3 | Timezone handling | PARTIAL | Google interval/offset parsing; future-age bound | Full instant storage and DST/zone migration deferred |
| 4 | Cache-first startup and resume | PARTIAL | Network launch no longer blocks Room collection; ON_RESUME re-checks freshness after the first resume; the hourly graph states when a day is not downloaded | S6 DONE; visible age/error states remain |
| 5 | Refresh ownership and coverage | PARTIAL | distinctUntilChanged; widget coverage target now reads the saved setting and selected provider limits; S2/S5 are implemented | Broader request coalescing and fallback-provider coverage remain open |
| 6 | Manual refresh and cancellation | PARTIAL | In-flight guard; success-only cooldown; cancellation rethrown in the worker. Provider propagation holds through coroutineScope, and mutation testing showed no test can falsify the runCatching blocks — the claim is narrowed, not proven | Shared app/widget manual policy; elapsed-time cooldown |
| 7 | Lifecycle policy | PARTIAL | Startup/boot periodic scheduling, screen wake and interval settings guard on active widgets; final-widget removal cancels periodic work and tracking | Permission-return tracking registration lacks a widget guard; runtime wake limitation remains |
| 8 | Location quality | PARTIAL | Explicit London default label; replacement remains enabled; last-known fixes more than two minutes in the future are rejected | S2 and S3 DONE; accuracy/provenance gaps remain |
| 9 | Request volume and rendering cost | PARTIAL | Request-count, payload and rasterisation tests present; scoped background requests; populated-widget placeholder preservation | S1/S5/S6 interactions; launcher, live traffic and device battery verification |

Completed substeps remain DONE; their broader findings remain PARTIAL when acceptance work is still missing. Estimates and test counts from the original audit are historical. The former hardcoded widget coverage target is fixed; fallback-provider coverage and the new S1-S6 interactions are not marked complete.

### 1. Wrong data, invisible — Give air quality and pollen their own freshness timestamps

**Current status — DONE for independent section ages: entity columns, migration and provider reuse policies are implemented, including unknown-age handling. Repository early-return integration is DONE as S5; user-visible age remains separate work.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** two columns plus a Room migration. No cheap partial version.

**Evidence:** `GoogleWeatherProvider.fetchWeatherData()` and `OpenMeteoWeatherProvider.fetchWeatherData()` pass `cachedData.currentWeather.lastUpdated` into both `isAirQualityFresh()` and `isPollenFresh()`. Both weather mappers assign a new `LocalDateTime.now()` on a successful weather fetch. `CurrentWeatherEntity` has one `lastUpdated` and no independent air-quality timestamp.

**Failure example:** air quality is fetched at 09:00. A weather-only refresh at 09:30 reuses that air quality and advances the shared timestamp to 09:30. At 10:00 the one-hour-old air quality appears only 30 minutes old. Repeated refreshes can keep old air quality eligible for reuse indefinitely; pollen age can also be understated, subject to date-coverage checks. Failed optional requests can preserve old values under the new weather timestamp too.

**Change:** add `airQualityLastUpdated` and `pollenLastUpdated` to `CurrentWeatherEntity`. Reusing a section must preserve its own timestamp rather than inheriting the weather fetch time. Keep the existing one-hour AQ and six-hour pollen TTLs initially as app policies, then tune from provider contracts and measured use.

Scope note: the first revision of this document proposed separate timestamps for current, hourly, daily, air quality and pollen. Only air quality and pollen have independent TTLs today, and only they can be reused across a refresh, so only they can exhibit the failure above. Two columns fix the described bug; five columns would be designing for TTLs that do not exist. Retain provider observation timestamps separately where already supplied, and revisit hourly/daily timestamps if and when those sections gain their own refresh policy.

**Acceptance:** successive current-weather refreshes do not advance AQ/pollen timestamps; expired optional data is retried; optional failure keeps old data visibly stale without falsely advancing its age. Add migration coverage for existing caches with unknown section age.

### 2. Wrong data, visible — Keep the city name and weather from the same snapshot

**Current status — PARTIAL: weather-owned display metadata, fetch-before-location-save, optional-cache eligibility, old-city hourly invalidation and cumulative movement checks are DONE. Providers are no longer offered optional cache with unknown or significantly different weather coordinates. These are still separate location/weather writes; coherent observation remains OPEN as S4.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** the label fix is small and needs no reordering. Atomic publish is a separate, larger decision.

This is the highest-priority finding in the document. It is the only one where the user can look at the widget and read a value that is plainly false.

**Evidence:** `WeatherUpdateWorker.doWork()` saves the detected location before fetching its weather. `WeatherDetailViewModel.refresh()` follows the same order. `WeatherRepositoryImpl.getWeatherData()` combines the latest location row with weather cached by that row's ID. The repository also passes existing cache into the provider after relocation.

**Failure example:** London weather is cached; the phone moves to Brighton; the location row is updated; HTTP fails. The cache can now display Brighton above London's temperature. Even if core weather succeeds, still-“fresh” cached London AQ/pollen can be reused for Brighton. A later automatic retry compares the already-updated location row to Brighton and may no longer recognize the original relocation.

**Change, first step:** associate cached weather with the coordinates and provider that actually produced it, and label the display from the weather snapshot's own location rather than from the latest location row. This removes the mismatch on its own, without reordering the worker or the ViewModel, and it is the change to make first.

**Change, second step:** once the label is honest, decide separately whether to fetch for a candidate location and atomically publish location and matching weather together. That reordering touches `WeatherUpdateWorker.doWork()` and `WeatherDetailViewModel.refresh()` and is worth doing deliberately, with the visible bug already gone rather than as a prerequisite to fixing it.

**Change, either way:** never reuse optional sections across a significant location change. Compare movement against the last successful weather coordinates so several small moves cannot silently move the label far from the cached forecast.

**Acceptance:** travel followed by an HTTP failure retains a correctly labelled old snapshot or shows “Updating location”; retry still knows the weather belongs elsewhere; a successful move replaces all sections or marks unavailable ones explicitly.

### 3. Mixed — Fix the timezone bugs now, defer the timezone migration

**Current status — PARTIAL: interval/offset conversion, malformed-hour rejection and the future-timestamp bound are implemented. Persisted unzoned timestamps and DST/zone migration remain deferred.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** two small bug fixes; one large migration that should wait.

This finding was previously a single P1 covering both. They are not the same size and do not carry the same value.

**Evidence:** `WeatherData.locationZoneId()` always returns the device zone. Open-Meteo requests that zone, but `GoogleWeatherMapper.mapHourly()` creates an unzoned `LocalDateTime` directly from `displayDateTime`. Google sunrise/sunset values are converted using the provider's location zone. Cache freshness also uses unzoned `LocalDateTime`, and time-change handling redraws existing cache without normalizing its stored timestamps.

**Impact:** when device and weather zones differ, Google hourly selection and freshness checks can compare different local clocks. Travel, daylight-saving changes and manual clock rollback can make stored cache ages misleading. The freshness predicate has no upper bound rejecting future `lastUpdated` values.

**Change now (small):** parse Google's interval timestamp or its explicit UTC offset instead of discarding the offset in `GoogleWeatherMapper.mapHourly()`. Stop presenting parse failures as `now` — a failed parse should mark the hour unavailable, not silently claim the current time. Add an upper bound to the freshness predicate so an implausibly future `lastUpdated` triggers revalidation instead of reading as fresh forever. On time/timezone changes, redraw immediately and reassess cache validity. These are genuine defects, independent of any schema work.

**Change later (large, deferred):** storing hourly instants and successful-fetch times as UTC epoch values or `Instant`, converting to the device zone at display time, keeping daily forecasts associated with the provider's forecast date and zone, and preserving sunrise/sunset instants for comparison. This is correct, and it is also a migration plus a mapper and display-layer rewrite.

The reason to defer: this is a current-location-first product, so device zone and forecast zone almost always agree, and the divergence this migration protects against is one users cannot presently observe. The cost becomes justified when per-widget cities or world clocks arrive, since those make zone mismatch the normal case rather than the exception. Until then, mark zone-dependent cached hours invalid when their original zone differs and accept the conservative refetch.

**Acceptance:** London device/New York forecast, crossing midnight, spring-forward, the repeated autumn hour, timezone change with no network, and manual rollback all select the correct forecast instant or clearly identify unavailable data. Google documents interval timestamps and display offsets in its hourly response. [Google hourly forecast](https://developers.google.com/maps/documentation/weather/hourly-forecast)

### 4. Perceived quality — Show cached weather immediately and refresh on return

**Current status — PARTIAL: cache-first collection, the resume freshness hook and the permission Compose startup gate are DONE. Richer age/error UI remains open, and full Compose/device acceptance remains outstanding.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** moderate, but it touches no schema and needs no migration, so it can land independently of findings 1 and 2. It is also the change users feel most directly, which is why it moved earlier in the implementation order.

**Evidence:** `WeatherDetailViewModel.loadWeather()` calls `ensureFreshWeatherAndWidgets()` before collecting the cached weather flow. A stale cache therefore waits behind network work. The detail screen's `ON_RESUME` handler refreshes permission state only.

**Change:** subscribe to Room immediately, render the last successful snapshot, and run freshness checks concurrently under the ViewModel lifecycle. On resume, request a freshness-gated refresh and resolve current location when appropriate. Retain visible data through errors. Expose “Updated at …”, refreshing, waiting for connection, and failed-refresh states without replacing useful cached content with a loading screen.

**Acceptance:** stale-cache startup on a slow or unavailable network displays cache before HTTP completes; returning to an existing screen after a long absence triggers one freshness check; no-cache failure has a visible retry path.

### 5. Mixed — Give refresh side effects one owner

**Current status — PARTIAL: distinctUntilChanged, the configured widget coverage target, requested optional-section freshness and newest-fix retention are DONE. BaseWidgetUpdater reads the saved range and selected provider limits instead of demanding a hardcoded seven days. Broader request coalescing and fallback-provider coverage remain open.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** one line, then an architecture change. Do not let the second block the first.

**Evidence:** settings provider changes directly force a refresh; `WeatherDetailViewModel` also observes provider and forecast-day changes and forces refreshes. Its provider flow uses `map(...).drop(1)` without `distinctUntilChanged()`, so unrelated DataStore emissions can trigger it. The repository mutex serializes forced requests but does not merge them. Background callers use the selected interval as TTL; the detail use case defaults to provider TTL. Widget freshness checking hardcodes seven requested forecast days while the worker uses the saved setting.

**Change now (one line):** add `distinctUntilChanged()` to the provider preference observation. The missing operator means unrelated DataStore emissions trigger real refreshes today; this is a defect with a trivial fix and no design dependency.

**Change later:** choose one existing application-level entry point to own refresh effects. Keep ViewModels observing data. Merge overlapping requests for the same location/provider/configuration; read the latest configuration when starting and prevent old results from overwriting a newer selection. Use one policy function for effective forecast coverage and freshness, with an explicit foreground/background distinction if wanted. Clamp coverage to actual provider capability so fallback cannot create an impossible freshness target.

**Acceptance:** changing units causes no HTTP calls; one provider switch produces one intended refresh; simultaneous app/widget refreshes share in-flight work; changing forecast length is honored without duplicate fetches or endlessly stale short forecasts.

### 6. Mixed — Make manual refresh predictable and failures actionable

**Current status — PARTIAL: the app in-flight guard, success-only cooldown and explicit worker/repository cancellation rethrow are DONE. Provider optional requests remain inside coroutineScope; existing cancellation tests establish scope cancellation, not that each runCatching block rethrows. Shared app/widget manual policy, visible outcomes and elapsed-time cooldown remain open.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** two small fixes carry most of the value; the throttle redesign is separable.

**Evidence:** the app records `lastRefreshTimeMs` before the attempt and silently blocks additional attempts for five minutes. Failure only logs a warning. Widget refresh bypasses this app throttle and refreshes every saved location. Broad `Exception`/`runCatching` handlers in the repository, providers, location lookup and worker can also catch coroutine cancellation.

**Change now (two small fixes):** record `lastRefreshTimeMs` on success rather than before the attempt. As written, a failed refresh locks the user out of retrying for five minutes with no explanation, which is the actual user-facing complaint in this finding and a two-line fix. Separately, rethrow `CancellationException` before the error fallback and retry logic, so cancelled work does not trigger a fallback provider request.

**Change later:** disable duplicate taps while a refresh is running; use a short, visible successful-request cooldown only if required. Use the same manual-refresh policy in the app and widget. Keep the existing all-location behavior until a scoped-location product is introduced. Distinguish transient network/server errors from configuration/authentication errors; respect provider retry guidance and preserve cached values.

Use `SystemClock.elapsedRealtime()` for in-process tap cooldowns, screen-wake throttles and location-fix age comparisons. It measures elapsed time including sleep and is unaffected by wall-clock changes. Use persistent UTC timestamps for cache records across process death/reboot; elapsed time resets on boot. UTC storage alone does not protect against manual clock changes, hence the revalidation rule above. [Android SystemClock reference](https://developer.android.com/reference/android/os/SystemClock)

**Acceptance:** a failed manual refresh can be retried; simultaneous taps do not queue repeated forced downloads; clock rollback does not suppress refresh for hours; cancelled work does not start a fallback provider request.

### 7. Reliability — Close lifecycle gaps without adding aggressive background timers

**Current status — PARTIAL: startup/boot periodic gates, boot/wake active-widget checks, the settings call to WeatherUpdateScheduler.scheduleIfWidgetsActive and final-widget cancellation are DONE. Permission-return handlers in both ViewModels still register passive tracking without checking for a widget. The runtime wake receiver still depends on process lifetime.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** moderate, no cheap subset. Lower value than findings 1–6; schedule it after them.

**Evidence:** application startup checks for active widgets, and removing the final widget cancels periodic work. However, boot unconditionally schedules periodic work and passive tracking; screen wake unconditionally queues immediate work; changing the refresh interval also schedules work. The runtime screen receiver exists only while the app process lives.

**Change:** apply the active-widget policy consistently to boot, wake and settings scheduling, while supporting explicit foreground refresh without a widget. Keep the host's existing 30-minute update callback as a recovery path until real-device evidence supports changing it. Describe wake refresh as opportunistic; it cannot promise recovery on every unlock after process death. Bound receiver work and hand network operations to WorkManager.

A requested refresh interval is approximate. WorkManager's minimum periodic interval is 15 minutes and actual execution depends on constraints and system optimizations. Communicate this in settings rather than promising exact updates. [Android work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

**Acceptance:** reboot with no widgets does not restart widget-only periodic work; removing the last widget stops background tracking; adding one restores work; clock keeps advancing offline after process death; delayed weather remains labelled with its last successful update.

### 8. Honesty, then reliability — Improve location quality and reduce unnecessary location work

**Current status — PARTIAL: explicit London-default labelling and bounded last-known fix age are DONE. Fixes more than two minutes in the future are rejected; smaller clock skew is tolerated. isCurrentLocation remains true to permit automatic replacement and is not proof of a real fix. Elapsed-time age, accuracy enforcement and fix provenance remain open; S2/S3 are DONE in code, with full WorkManager/device validation outstanding.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** one small honesty fix; the validation policy is larger.

**Do first:** no-fix initialization currently uses London coordinates *marked as the current location*. The app tells the user it knows where they are when it does not. That is cheap to fix, and it should be labelled as a default city or a prompt to choose one.

**Evidence:** the passive receiver uses displacement without validating fix age or accuracy and discards the triggering fix before the worker requests another. `getCurrentLocation()` accepts last-known fixes by wall-clock age, can fall back to a six-hour-old fix, and attempts high accuracy after balanced accuracy times out. Manual app refresh requests location even before deciding whether the selected location is fixed. No-fix initialization uses London coordinates marked as current location.

**Change:** retain validated fix metadata across the handoff; reject out-of-order, too-old or implausibly inaccurate fixes. Set explicit age/accuracy policies appropriate to weather and test approximate-location grants. Use balanced/passive fixes by default; reserve high accuracy for an explicit foreground location action when useful. Propagate coroutine cancellation to location requests. Skip location lookup for fixed locations. Label no-fix fallback as a default city or ask for city selection, instead of implying successful location detection.

**Acceptance:** old/poor fixes do not cause city bouncing; accepted relocation is not lost when a second lookup fails; permission denial still permits saved-city weather; an unavailable fix never masquerades as the user's current position.

### 9. Cost — Local measurements implemented; live/device measurements remain open

**Current status — PARTIAL: endpoint baseline tests, scoped background refresh, payload/rasterisation tests and placeholder preservation are DONE in code. Historical local measurements are recorded below and in refresh_improvements.md; they were not rerun here. Live endpoint counts, actual bills, launcher rendering and device battery measurements remain unverified. S1-S3 and S5-S6 address correctness and request gaps around the scoped refresh; S4 remains open.**

The evidence and proposed changes below are from the original audit; use the status above to distinguish completed work.

**Effort:** measurement is cheap and should happen early. Acting on it is not, and should wait for the numbers.

**Evidence:** Google refresh requests current conditions, hourly pages, daily forecast and optional data. Seven days at `pageSize = 24` can mean seven sequential hourly requests per refresh. Every widget provider update also pushes a fallback layout before cached content, and application startup performs multiple blocking DataStore reads.

**Why this is not just a battery question:** the Google Weather API is billed per request. Seven sequential hourly pages, multiplied by saved locations, multiplied by roughly forty-eight refreshes a day at the default interval, is a per-user running cost — on a branch that has just added AdMob interstitials to generate revenue. Unit economics depend on both sides of that ratio. This is the strongest reason to move measurement earlier than its priority rank would otherwise suggest, even though acting on the result stays late.

**Change:** count endpoint requests and measure startup/render time first. Keep enough hourly coverage for the current detail UI; do not truncate an existing feature solely to lower request counts. Then consider a supported larger page size, independent forecast TTLs or loading distant hours on demand. Consolidate startup preference reads after measuring cold-start impact.

**Widget rendering caveat:** preserving last rendered widget content instead of pushing a fallback layout must be validated on both rendering paths, not one. The app gates RemoteViews behaviour at API 31, so pre-Android-12 and Android 12+ hosts take different code paths and can differ in whether a preserved view survives a routine update. Check an API 29 host as well as a current one before concluding the change is safe, and treat OEM launchers as a separate case again.

**Acceptance:** record before/after HTTP counts, cold-start time and widget redraw latency; inspect launcher updates for flicker; demonstrate savings without reducing forecast coverage. Google's API supports paginated hourly requests, so endpoint/page counts matter more than just the number of worker runs. [Google pagination](https://developers.google.com/maps/documentation/weather/hourly-forecast#specify_number_of_hours_to_return_per_page)

## Proposed sync policy

| Trigger | Local action | Network action |
| --- | --- | --- |
| Minute changes | Launcher updates clock via `TextClock`. | None. |
| Date/timezone/manual time change | Redraw date and time-sensitive weather selection; validate cached time basis. | Refresh only if cache is invalid or stale. |
| Open/resume app | Observe and display Room cache immediately. | One freshness-gated request; resolve current location when needed. |
| Periodic work | Preserve cached content. | Refresh due sections for the relevant saved locations when network constraints allow. |
| Manual refresh | Show visible progress and keep old data. | Bypass normal core-weather TTL; coalesce in-flight work and respect provider limits. Optional sections retain independent TTLs. |
| Valid significant movement | Keep the last correctly labelled snapshot pending replacement. | Fetch for new coordinates; publish matching location/weather atomically. |
| Screen wake | Redraw cache when the runtime receiver is available. | Coalesced stale check when widgets are active. |
| Connectivity returns | Clear waiting state when work proceeds. | Let constrained queued work resume; foreground UI may request a deduplicated check. |
| Provider/forecast setting | Show the selected configuration and refresh status. | One refresh owned by the shared entry point. |
| Units/theme/text size | Reformat and redraw existing data. | None. |

The clock being current does not imply weather is current. Display weather fetch age separately from location age and provider observation time. A small details view can show provider, last successful refresh, last error, approximate scheduled cadence, and whether background location is available. Keep raw coordinates out of ordinary user-facing diagnostics and release logs.

## Implementation order and verification

Current queue, audited on 11 September 2026:

1. **PARTIAL, S4 and acceptance work remain.** S1-S3 and S5-S6 are implemented in code; make Room observation coherent only if further evidence justifies it, then complete WorkManager, Compose, launcher and device checks. Full evidence and proposed checks are in [refresh_improvements.md](../refresh_improvements.md#8-weather-sync-review--11-september-2026).
2. **OPEN, launcher verification.** Check placeholder preservation, first placement, process death, launcher restart, reboot and resize on API 26-30 and API 31+, including Xiaomi/MIUI. Observe the weather page while missing hours are fetched and during background relocation. Existing code/tests do not replace these checks.
3. **OPEN, remaining gaps.** Guard permission-return passive registration when no widget exists; retain the separate shared manual-refresh policy, elapsed-time cooldown, fix-accuracy and visible age/error work.
4. **Deferred by design.** The full `Instant` migration, broader location+weather atomic publication and optional render deduplication. Deferring those larger changes does not defer S1-S6.

**DONE prerequisites:** variant-aware AdManagerTest, weather-owned location metadata, safer relocation save order, section-age migration and provider reuse policies, Google offset handling, future-timestamp rejection, distinct provider observation, configured widget coverage, optional-cache ownership guard, manual in-flight/success guard, worker cancellation rethrow, explicit default-city label, bounded last-known age, startup/boot/wake/interval scheduling guards, batched widget snapshots, populated-widget placeholder preservation and background refresh scope. These scoped completions do not close the broader PARTIAL findings above.

Deferred, revisit when per-widget cities or world clocks are on the roadmap: the full `Instant` migration in finding 3.

Follow the repository's Red → Green → Refactor requirement for implementation. Use real Room databases, DataStore files, domain values and platform components; no mocks, stubs or test doubles in new tests. Extract pure decisions where possible and exercise them with explicit timestamps/coordinates. Network and location integrations requiring provider/platform behavior need live integration or device validation; do not substitute source-string tests for those behaviors. Run the full unit suite after each coherent implementation group and report device checks separately.

| Scenario | Required result |
| --- | --- |
| Airplane mode with cached data | Clock advances, weather remains visible with honest age, failed refresh does not erase it. |
| No cache and no location permission | Clear no-data/default-city state and usable city-selection/retry path. |
| Overnight Doze, then unlock | Host clock is current; cache remains labelled; eligible recovery work eventually refreshes weather. |
| Process death, reboot and app update | Widgets restore from persistent state; no reliance on an always-alive process. |
| London → Brighton, HTTP failure, later retry | City and weather never mismatch; subsequent retry still targets Brighton. |
| Different device/provider zones and both DST transitions | Correct hour selection and date semantics, including the repeated hour. |
| AQ/pollen expired while core weather succeeds | Optional section age remains unchanged until that section actually refreshes. |
| Provider switch plus manual tap plus periodic worker | No redundant equivalent downloads or late writes from obsolete configuration. |
| Last widget removed, then reboot | No widget-only tracking or periodic weather work. |
| Android API 26–30 and API 31+, including an OEM launcher | Clock geometry, date rollover, cache recovery and routine update flicker checked visually. |

## Relationship to earlier plans

Use this review as implementation history alongside [refresh_improvements.md](../refresh_improvements.md), whose status matrix and section 8 track the latest work. Both documents were reconciled against source on 11 September 2026. [APP_IMPROVEMENT_PLAN.md](APP_IMPROVEMENT_PLAN.md) and other earlier task lists may still contain stale descriptions; recheck them before implementing work. Existing local-only scope and launcher-watchdog decisions remain unchanged.

## Source navigation

The named functions above can be found in these implementation files:

- Scheduling: [WeatherUpdateScheduler.kt](../app/src/main/java/com/clockweather/app/worker/WeatherUpdateScheduler.kt), [WeatherUpdateWorker.kt](../app/src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt).
- Cache ownership: [WeatherRepositoryImpl.kt](../app/src/main/java/com/clockweather/app/data/repository/WeatherRepositoryImpl.kt), [CurrentWeatherEntity.kt](../app/src/main/java/com/clockweather/app/data/local/entity/CurrentWeatherEntity.kt).
- Section freshness: [GoogleWeatherProvider.kt](../app/src/main/java/com/clockweather/app/data/provider/GoogleWeatherProvider.kt), [OpenMeteoWeatherProvider.kt](../app/src/main/java/com/clockweather/app/data/provider/OpenMeteoWeatherProvider.kt), [WeatherFreshness.kt](../app/src/main/java/com/clockweather/app/domain/model/WeatherFreshness.kt).
- Time conversion: [GoogleWeatherMapper.kt](../app/src/main/java/com/clockweather/app/data/mapper/GoogleWeatherMapper.kt), [WeatherData.kt](../app/src/main/java/com/clockweather/app/domain/model/WeatherData.kt).
- UI and settings: [WeatherDetailViewModel.kt](../app/src/main/java/com/clockweather/app/presentation/detail/WeatherDetailViewModel.kt), [SettingsViewModel.kt](../app/src/main/java/com/clockweather/app/presentation/settings/SettingsViewModel.kt).
- Widget rendering: [BaseWidgetUpdater.kt](../app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt), [BaseWidgetProvider.kt](../app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetProvider.kt).
- Location: [LocationRepositoryImpl.kt](../app/src/main/java/com/clockweather/app/data/repository/LocationRepositoryImpl.kt), [LocationUpdatesReceiver.kt](../app/src/main/java/com/clockweather/app/receiver/LocationUpdatesReceiver.kt).
- Lifecycle: [ClockWeatherApplication.kt](../app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt), [ScreenWakeReceiver.kt](../app/src/main/java/com/clockweather/app/receiver/ScreenWakeReceiver.kt), [BootCompletedReceiver.kt](../app/src/main/java/com/clockweather/app/receiver/BootCompletedReceiver.kt).

## Verification of the original pass — 5 September 2026 (historical)

These results describe the initial audit before the implementation follow-up. They are superseded by the later 352-test results below and do not describe the current suite or current work order. Subsequent status revisions rechecked the source paths named in their current-status markers; they did not rerun runtime tests.

- Source review completed across clock/widget rendering, receivers, WorkManager scheduling, repositories, provider mappers, cache entities, settings and detail-screen loading.
- Official Android and Google documentation consulted on 5 September 2026; linked next to the recommendations they support.
- An independent agent cross-checked the section-age, timezone, cache-first loading and relocation findings against source.
- XML report totals from that test run: debug — 316 tests, 0 failures/errors/skips; release — 316 tests, 1 failure, 0 errors/skips. These are separate build variants, not 632 distinct test cases.
- The original `.\gradlew.bat test --no-daemon` run failed in `:app:testReleaseUnitTest`: AdManagerTest expected ADS_ENABLED to be false in the release variant. That assertion was subsequently made variant-aware and the later full suite passed. It is no longer a blocker or a pending implementation step.
- No device, emulator, production-provider request or battery measurement was performed in the original pass. The acceptance matrix remains proposed verification work. Finding 9 still requires measurement; this historical test report supplies no request-count or billing evidence.
- The 5 September revision re-scoped findings 1, 2, 3, 5, 6 and 8 into cheap and expensive halves, demoted the deferred timezone migration, added the API cost and pre-Android-12 widget rendering considerations, and reordered implementation. The underlying source observations were not re-verified against the tree during that revision; the evidence blocks still date from the original pass.

## Implementation follow-up — 5 September 2026

The sections above combine current status markers and a revised work queue with explicitly historical evidence. The following implementation changes were completed before those documentation revisions:

- Google hourly interval timestamps now convert to the device timezone. The display-time fallback accepts Google's seconds-format offsets and ISO offsets; malformed timestamps are dropped.
- Passive relocation resolves location metadata from the supplied coordinates instead of retaining the previous city name.
- The explicitly labelled London default remains eligible for replacement by a real location fix.
- Both weather providers use independent section-age policies. Unknown AQ/pollen ages are stale, including caches upgraded from version 4.
- Manual refresh rejects overlapping requests, records cooldown only after success, and permits retry after failure. It awaits the download before releasing the refresh guard.
- Manual relocation of an existing row now downloads weather before saving changed location metadata, protecting upgraded caches whose weather-owned location fields are still null.

Last completed implementation test run — 5 September 2026 (not rerun for later documentation revisions):

- `.\gradlew.bat test :app:lintDebug --no-daemon` completed successfully.
- Debug: **352 tests**, zero failures, errors or skips. Release: **352 tests**, zero failures, errors or skips. These counts represent the same suite in two build variants.
- Lint: zero reported errors, 93 warnings and two hints. Existing baseline suppression remains configured.
- `git diff --check` passed; temporary failing-test mutations were removed.
- The new [location persistence regression](../app/src/test/java/com/clockweather/app/domain/usecase/RefreshWeatherLocationPersistenceTest.kt) uses real Room, DataStore, repositories and Retrofit clients against an unavailable local endpoint. It failed with London replaced by Berlin under the old save order, then passed after correcting the order; it also checks the cached weather still displays London.
- The snapshot persistence test now uses real Room and DataStore rather than mocked DAOs. The refresh guard has real state-transition tests. Earlier legacy tests elsewhere still use doubles.

Cheaper agents implemented and cross-reviewed the main fixes. The orchestrator completed the last save-order correction and final checks after agent usage limits interrupted delegation. Device/launcher behavior, live reverse geocoding, production providers and battery/Doze performance have not been verified. The broader deferred roadmap, including a full persisted-`Instant` migration, remains separate from these corrections.

## Implementation commit checkpoint — 5 September 2026

The previously pending 40 application source/test files are committed together with this checkpoint. Project-specific, model-neutral agent guidance was committed separately as `fe11370`; the audit was first committed as `85fb66d`. Baseline measurement and subsequent queue items have not started.

An independent agent rechecked the manual save order, refresh guard, migration isolation, passive relocation and unknown section ages. The commit preparation changed only two trailing spaces in migration-test SQL, so the suite was rerun rather than assumed: `.\gradlew.bat test --no-daemon` passed against the committed tree — 352 tests in each of debug and release, zero failures, errors or skips, with no source file modified after the reports were written. A fresh `.\gradlew.bat :app:lintDebug --no-daemon` command passed: Gradle considered its analysis/report up to date, with zero errors, 93 warnings and two hints. No device, launcher, live-provider or battery verification was added, and the open findings remain open.

## Queue steps 1-4 implemented — 6 September 2026

Committed as `4d64582`, `72471fb`, `7531367` and `c7fb57f`. Suite green in both variants after each: 368 tests, zero failures, errors or skips. Lint: zero errors, 95 warnings.

- **Step 1, baseline measured.** A real HTTP server, routed by method and path because the Google provider fetches concurrently. Google 7-day empty cache costs **12 requests** (7 hourly pages, 1 current, 1 daily, 1 pollen, 1 Open-Meteo pollen fallback, 1 air quality); 10-day costs **15**; a 7-day refresh with fresh optional sections costs **9**, which demonstrates finding 1's reuse working. Open-Meteo's full empty-cache path costs **2** at the tested horizons: one forecast response including hours, plus one Air Quality request combining AQI and pollen. With reusable or out-of-scope optional data, a background refresh can need only the forecast request. Counts are asserted for the tested scenarios; these are historical local measurements, not live traffic.
- **Step 2, corrected diagnosis.** The audit blamed the hardcoded seven-day target, but the saved setting is also seven, so reading it changes nothing alone. `ForecastWidgetUpdater` declared seven required *future* days, making the target eight covered days, which no selectable length delivers — that widget never read as fresh and enqueued a refresh on every callback. It renders five rows from today, so it needs four. `FORECAST_WIDGET_ROW_COUNT` is now the single source for both.
- **Step 3, movement guard.** `refreshAndPersist` withholds the cache when the weather row's own coordinates are absent or at least 5 km from the requested location. The weather row's coordinates are used, not the mapped domain location, because `getWeatherData` substitutes the location row's coordinates for null ones and those have already moved.
- **Step 4, placeholder.** Limited to widgets with nothing to preserve, tracked in `WidgetRenderState` (SharedPreferences, because the decision happens synchronously in a broadcast receiver and must survive process death).

No device, launcher, live-provider or battery verification was performed. The request counts come from a local server exercising the real provider code, not from production traffic or a bill.

## Resume freshness implemented — 6 September 2026

`WeatherDetailScreen`'s ON_RESUME calls `onResumed()` rather than `refreshPermissions()`. Every resume after the first runs a freshness-gated check; a failure is logged and leaves the cached weather on screen. `ResumeFreshnessGate` suppresses the resume that arrives with the composition that constructed the ViewModel, whose init already issued that check. Its state transitions have real-object tests. **10 September correction:** this does not prove a cold start downloads once; the permission Compose effect can independently force a second request (S6).

Suite green in both variants: 371 tests, zero failures, errors or skips. Lint: zero errors.

## Findings 6-8 and the cost question — 6 September 2026

Committed as `d2a7939` and the relocation regression alongside it. Suite green in both variants: 382 tests, zero failures, errors or skips. Lint: zero errors.

- **Cancellation (6).** The worker's two broad catches rethrow. The provider half does not reproduce: `fetchWeatherData` runs inside a `coroutineScope` and reaches its results through `await()`, so a cancelled parent propagates whatever the inner `runCatching` does. `ProviderCancellationTest` hangs only the optional endpoints, so a fetch completing after cancellation could only have swallowed it; both providers pass. The `runCatching` blocks are therefore left alone and the test guards the property instead of defensive code guarding itself.
- **Settings scheduling (7).** `setWeatherRefreshInterval` routes through `scheduleIfWidgetsActive`. The interval is still stored with no widget placed, and `onEnabled` applies it when one appears.
- **Fix age (8).** `isLastKnownFixAcceptable` bounds the age below as well as above, with two minutes of tolerance. Previously a fix stamped in the future gave a negative age that passed every threshold — which is what a clock rolled back under a stored timestamp produces.
- **Provenance (8), not done.** Every reader of `isCurrentLocation` wants follow-device semantics, which is correct for the labelled default, so a schema column separating provenance has no consumer. Revisit when something needs to say "we could not find you" differently from "follow the device".

### Cost note — superseded on 6 September 2026 by the scoping change recorded below

This was the position before the background scope was narrowed. Its reasoning about the two suggested levers still holds and is why a third was taken instead.

Hourly pages are 7 of the 12 requests in a default Google refresh, so they are the whole cost question. Neither lever the original audit suggested is available:

- **A larger page size does not exist.** Google caps the hourly endpoint at 24 hours per page, so 7 days is 7 requests by construction.
- **Truncating coverage would remove a feature.** `scopedHourlyForecasts` gives the detail screen a 24-hour graph *for the selected day*, and the user can select any day in the forecast. The distant hours are displayed, not merely fetched.

What remained was loading distant hours on demand. That was taken, in a form that avoids the merge this note anticipated: background refreshes buy no hourly data at all and leave whatever is cached alone, so the hours are still written wholesale by a single foreground fetch. See the scoping section below.

## Background refresh scoped to the home screen — 6 September 2026

Committed as `c284140`, `794f96d` and `c25c9f9`. Suite green in both variants: 387 tests, zero failures, errors or skips. Lint: zero errors.

Hourly pages were 7 of the 12 requests in a full Google refresh and nothing on the home screen renders them; air quality is the same. `RefreshScope` now carries three flags, because whether pollen is worth buying depends on a user setting rather than on who is asking. A background refresh costs **4 requests with the widget's pollen bar on, 2 with it off**, and the app buys the rest when opened. `isWeatherDataFresh` gained `requireHourly` so callers that never render hours are not held stale by their absence — demanding it would re-enqueue work on every host callback, the failure fixed in `72471fb`.

Cached hours survive background refreshes and are replaced wholesale by a foreground fetch. **10 September correction:** wholesale retention does not establish location ownership, freshness or validity after a timezone change. Relocation can attach old-city hours to new current/daily weather (S1), and optional-section freshness can be skipped on resume (S5). The full `Instant` migration remains deferred; these scoped correctness fixes remain open.

### What independent review changed

Four reviewers examined this work; five defects were theirs, not mine to claim as found.

- The hourly graph returned without composing anything when a day had no cached hours, leaving an unexplained gap rather than the "brief wait" the design assumed. It now says so.
- `SettingsViewModel`'s provider-change refresh took the background scope by omission, populating a newly chosen provider without hourly or air quality. Its existing test asserted the old two-argument call and passed.
- `BaseWidgetProvider` recorded a widget as rendered even when `updateWidget` had swallowed a failure, suppressing the placeholder for a widget showing nothing — reintroducing what the placeholder exists to prevent. `updateWidget` now reports success.
- `WidgetRefreshSchedulingTest` could not catch `requireHourly` being restored, because every fixture carried a full day of hours. An empty-hourly case now covers it, verified red under that mutation.
- `ProviderCancellationTest` claimed more than it proves. `coroutineScope` rethrows on a cancelled Job whatever the body does, so wrapping the whole fetch in `catch(Throwable)` leaves it green. Its docstring now claims only that the optional fetches stay inside a cancellable scope.

### Corrections to earlier figures in this document

The 12-request baseline is the empty-cache worst case, not a routine refresh: air quality expires hourly and pollen every six hours, so steady state before this work was about 9 requests and roughly **464 per day per location**, not the 576 quoted earlier. The cache-independent way to state the saving is requests removed per refresh, not a percentage against a worst case.

No device, launcher, live-provider or battery verification was performed. Request counts come from a local server exercising the real provider code, not production traffic or a bill.

## Home-screen freshness and flicker follow-up — 5 September 2026

Status audited 11 September: item 1 is DONE in code with device checks OPEN; item 2 remains OPEN; item 3 is PARTIAL (cache preservation and freshness checks exist; the S1-S3 and S5-S6 sync fixes are implemented); item 4 is OPEN; item 5's existing interval/watchdog policy is retained, with continuous-display verification OPEN. These fixes do not establish flicker-free launcher rendering or guarantee fresh weather whenever the user looks at the widget.

### Historical trigger and remaining evidence

- **Fixed in code:** `BaseWidgetProvider.onUpdate` and its failure handler now skip placeholder replacement when a successful render is recorded. The previous unconditional fallback triggered this review; launcher verification of first placement, process recovery and flicker remains open.
- `BaseWidgetUpdater.updateWidget` submits a full `RemoteViews` update even when the displayed content is unchanged. `TextClock` already ticks independently, so weather scheduling does not need to redraw clock digits each minute.
- `ScreenWakeReceiver` redraws cached content and enqueues freshness-gated work, but `ClockWeatherApplication` registers it at runtime. It cannot provide a recovery guarantee while the application process is absent. Remaining on the home screen also produces no repeated unlock event in this implementation.
- The existing WorkManager interval defaults to 30 minutes, with a configurable 15-minute minimum. All three widget providers retain a 30-minute host callback as a recovery watchdog. These are scheduling opportunities, not proof that data is current.

### Proposed behavior and implementation order

1. **Preserve the last useful display — implemented 6 September 2026, unverified on a launcher.** Remove the fallback-before-refresh sequence for an already rendered widget. Build the complete replacement from persistent cache, then publish it once. A failed network or rendering attempt must retain existing weather. Use an explicit first-placement/no-data state where there is no usable snapshot; verify initialization and recovery rather than relying solely on an in-memory initialized flag.
2. **Optional, after measurement: avoid redundant publication.** Compare the content that will actually be displayed before a routine redraw. Include weather, snapshot location, units, language, date, theme, widget dimensions and update-time text; exclude host-driven clock ticks. Force full publication for initialization, restore, resize and configuration changes. An optimization must never suppress the first render after process restart. An in-memory comparison can safely treat missing state as unknown and force a full render; persistence is not mandatory. A persisted per-widget marker is also not proof of the launcher's current rendered state and would need restore/deletion handling. Choose initialization recovery independently of optional deduplication. Start with fewer full updates; introduce partial weather updates only after validating the existing responsive layouts on both API 26–30 and API 31+. Android partial updates require an initial full update. See [widget update types](https://developer.android.com/develop/ui/views/appwidgets/advanced).
3. **Refresh without clearing content.** Keep the user's configured interval and the existing host watchdog during this change. On eligible wake, widget callback and app resume, inspect persisted freshness and request a deduplicated download only when needed. An explicit refresh tap may bypass age checks. Recheck freshness after acquiring the repository's refresh lock so overlapping automatic triggers can reuse a completed download. Publish the new coherent Room snapshot after success, including when temperature is unchanged but its fetch timestamp advances.
4. **Make age visible.** Display a compact absolute label such as “Updated 14:32”, with a date when it is from an earlier day, based on the last successful weather fetch. Keep provider observation time distinct wherever available; downloading an old observation does not make the observation new. Never advance the successful timestamp on failure. An absolute label remains truthful even when the process cannot update a relative “5 minutes ago” label. Update stale/error styling on the next render, without erasing weather; do not promise that styling changes at an exact deadline while background execution is deferred.
5. **Treat a continuously visible widget honestly.** Continue using periodic work while the home screen stays open; the current implementation has no reliable visibility-triggered refresh loop. Users who want more frequent checks can use the existing 15-minute option, with its battery/network tradeoff. WorkManager has a 15-minute minimum repeat interval and execution can be delayed or skipped by constraints and system optimizations. Therefore “never stale” is not an achievable offline/background guarantee. See [periodic work timing](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#periodic-work).

### Acceptance checks for this follow-up

- Real-object regression: a populated widget receives a routine refresh without an intermediate empty/default state; refresh failure preserves its content.
- Real-object regression: an unchanged render is skipped, but a successful fetch timestamp, date rollover, units/theme change, resize or restored widget still updates correctly.
- Real-device recording: keep the home screen open through a scheduled refresh and a clock minute boundary; no blank flash, clock reset or mismatched city/weather during publication.
- Repeat first placement, process death, launcher restart, reboot and resize on an API 26–30 device and an API 31+ device, including the affected Xiaomi launcher where available.
- Overnight Doze followed by unlock, and airplane mode followed by connectivity recovery: cached weather remains readable with its original successful-update time, then changes after an eligible successful fetch.
- Record actual fetch and publication times rather than asserting a guaranteed 15- or 30-minute completion deadline.

This follow-up changes documentation only. Source paths and Android guidance were checked; runtime tests and launcher recordings were not performed for these proposed changes.
