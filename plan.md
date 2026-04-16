# Clock Time-Sync Improvement Plan

Findings from analysing how `ClockWeatherWidget` keeps widget time in sync with the device clock. The current implementation is **solid** — multi-layered (TIME_TICK, AlarmManager, screen receivers, manifest fallbacks) with gap detection, digit-state storage, and recent fixes for process freeze / app update / device wake. Items below are incremental hardening, not correctness fixes.

Priority legend: **P0** — observable user-facing risk · **P1** — robustness / rare races · **P2** — code health / observability.

---

## P0 — Observable risk

- [x] **Prevent double-render when TIME_TICK is slow (>1200 ms)**
  - Symptom: On heavily loaded devices, `TIME_TICK_GRACE_MS = 1200L` expires, `ClockAlarmReceiver` does a backup push, then TIME_TICK finally arrives and pushes again in the same minute — two renders, potential flicker.
  - File: [ClockAlarmReceiver.kt:182](app/src/main/java/com/clockweather/app/receiver/ClockAlarmReceiver.kt:182) and [TimeTickReceiver.kt:27](app/src/main/java/com/clockweather/app/receiver/TimeTickReceiver.kt:27)
  - Fix: after TIME_TICK runs `markTimeTickObserved()`, skip the push if the current epoch minute is already rendered in `WidgetClockStateStore` (compare `lastRenderedEpochMinute`). Covers both orderings.
  - Test first (TDD): failing test in `TimeTickReceiverTest` proving that when alarm-backup has already rendered the current minute, TIME_TICK does not re-render.

- [x] **Clamp / self-heal orphaned animation-suppression windows**
  - Symptom: `markNoAnimationUntilEpochMinute()` writes a future epoch minute to prefs. If the process dies before that minute, the window is orphaned and animations stay suppressed forever for that widget.
  - File: [WidgetClockStateStore.kt:105-117](app/src/main/java/com/clockweather/app/presentation/widget/common/WidgetClockStateStore.kt:105)
  - Fix: add a sanity guard — if `untilEpochMinute - currentEpochMinute > MAX_SUPPRESS_WINDOW_MINUTES` (e.g. 5), drop the key and log. Never trust an arbitrary future value.
  - Test first: failing test that writes a 10-year-future suppression and asserts `shouldSuppressAnimation` returns `false` on the first call.

---

## P1 — Robustness / rare races

- [x] **Make TIME_TICK gap detection robust under concurrent delivery**
  - Symptom: `previousObservedMinute = get(); markObserved(current)` is not atomic. Two near-simultaneous TIME_TICK intents (theoretical, but observed on some OEM ROMs during time changes) can corrupt gap detection.
  - File: [TimeTickReceiver.kt:33-39](app/src/main/java/com/clockweather/app/receiver/TimeTickReceiver.kt:33) and [ClockWeatherApplication.kt:208-212](app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt:208)
  - Fix: replace the read-then-write pair with a single `AtomicLong.getAndSet(current)` and compute gap from the returned previous value.
  - Test first: failing concurrency test dispatching two TIME_TICK events on parallel dispatchers and asserting gap detection reports a single `+1` transition.

- [ ] **Protect baseline check + instant push under the refresh mutex**
  - Symptom: `areAllActiveWidgetBaselinesReady()` is read outside `widgetRefreshMutex`, then `pushClockInstant()` runs. A concurrent `refreshAllWidgets()` can be mid-write, so the TIME_TICK path may see stale baseline-ready state and take the slow path unnecessarily (or vice-versa).
  - File: [ClockWeatherApplication.kt:482-525](app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt:482) and [TimeTickReceiver.kt:44](app/src/main/java/com/clockweather/app/receiver/TimeTickReceiver.kt:44)
  - Fix: expose a `withClockMutex { ... }` helper and run the check + push inside it. Keep the critical section tiny.
  - Test first: test interleaving `refreshAllWidgets` + TIME_TICK and asserting TIME_TICK always sees a consistent baseline-ready snapshot.

- [x] **Re-check `hasAnyActiveWidgets()` before rescheduling the alarm**
  - Symptom: last widget removed mid-refresh → alarm still rescheduled → one wasted wake next minute.
  - File: [ClockAlarmReceiver.kt:40-47](app/src/main/java/com/clockweather/app/receiver/ClockAlarmReceiver.kt:40)
  - Fix: call `hasAnyActiveWidgets()` a second time right before `scheduleNextTick()`; skip if zero.
  - Test first: test that simulates widget removal between initial check and reschedule, asserts no alarm is scheduled.

- [x] **Guarantee WidgetPrefsCache is seeded before first `pushClockInstant`**
  - Symptom: cold-start race — `pushClockInstant` reads `getCachedSnapshot()` before the DataStore seed coroutine completes, falling back to `DateFormat.is24HourFormat(this)` which may disagree with the user pref for one tick.
  - File: [WidgetPrefsCache.kt:59](app/src/main/java/com/clockweather/app/presentation/widget/common/WidgetPrefsCache.kt:59) and [ClockWeatherApplication.kt:270](app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt:270)
  - Fix: do a synchronous `runBlocking` first-read in `ClockWeatherApplication.onCreate()` before any widget path can be triggered, or expose a `ready` latch and have `pushClockInstant` await it with a tight timeout.
  - Test first: test that invokes `pushClockInstant` immediately after `Application.onCreate` and asserts the rendered 24h/12h format matches the persisted preference.

---

## P2 — Observability & code health

- [x] **Add a structured clock-trace log line with "update source"**
  - Every push currently logs `CLOCK_TRACE` with varying fields. Normalise to one JSON-ish format: `source=TIME_TICK|ALARM|SCREEN_ON|USER_PRESENT|BOOT|PACKAGE_REPLACED|TIMEZONE minute=N gap=K forceAll=bool`.
  - Makes bug triage for "widget stuck" reports 10× easier — one grep tells you which path last ran.
  - File: across all receivers + `ClockWeatherApplication.pushClockInstant`.

- [x] **Record drift metric: `|systemMinute - lastRenderedMinute|` on each screen-on**
  - On `SCREEN_ON` before the forced push, compute the stale delta and log it. If this is ever > 1 for a non-Doze wake, we have a real bug.
  - File: [ScreenStateReceiver.kt:34-44](app/src/main/java/com/clockweather/app/receiver/ScreenStateReceiver.kt:34)
  - Zero code-path change, pure telemetry.

- [x] **Extract `TIME_TICK_GRACE_MS` and other tunables to a single `ClockTuning` object**
  - Currently scattered across receivers. One constants file makes the tradeoffs explicit and reviewable.

- [x] **Robolectric integration test covering the Doze → wake cycle**
  - Shadow AlarmManager + shadow screen state. Simulate: screen off → advance clock 20 min → SCREEN_ON. Assert: single full-digit push (`forceAll=true`), no intermediate renders, digit store reflects new time.
  - Catches regressions in the most user-visible scenario.

- [x] **Unit test for `PackageReplacedReceiver` baseline invalidation**
  - Commit 1b7dc7e fixed the "00:xx after update" bug but the regression test is implicit. Add a dedicated test: set stored digits to `(1,0,3,0)`, fire `MY_PACKAGE_REPLACED`, assert baselines cleared and `syncClockNow` called with `suppressAnimation=true, reassertAfterReschedule=true`.

---

## Explicitly NOT doing

- No redesign of the trigger layering — it is deliberately over-redundant and correct.
- No switch to `WorkManager` for clock ticks — minute-cadence clock work is exactly what `AlarmManager` + `TIME_TICK` is for; WorkManager's 15-minute minimum would regress accuracy.
- No removal of the manifest-declared `UnlockSyncReceiver` fallback — it is the last line of defence after process kill.
