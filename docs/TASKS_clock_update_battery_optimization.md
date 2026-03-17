# Tasks: Clock Update Accuracy & Battery Optimization

> **Goal:** Most accurate time display synced with the system clock while maximizing battery life — stop waking the device when the screen is off, degrade gracefully on low battery, and eliminate unnecessary per-tick overhead.

---

## Current State Analysis

| Aspect | Current Behavior | Problem |
|---|---|---|
| Alarm scheduling | `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` every 60 s | Wakes CPU every minute even when screen is off |
| High-precision toggle | `isHighPrecision` is **hardcoded `true`** in `scheduleNextTick()` | User setting (`KEY_HIGH_PRECISION`) has zero effect |
| Screen awareness | None — no `ACTION_SCREEN_ON/OFF` handling | Every tick wakes the device for invisible updates |
| Battery awareness | None — no `BatteryManager` checks | No throttling at 15 % or 5 % battery |
| Per-tick DataStore reads | `entryPoint.dataStore().data.first()` on every 60-s tick | Unnecessary I/O on hot path |
| Missed-tick recovery | Gap ≠ 1 → full widget rebuild (weather + clock) | A single 2-min gap triggers expensive weather re-fetch |
| `TextClock` | Not used | Could eliminate alarm overhead entirely for simple-digit variants |

---

## Task List (Priority Order)

### Task 1 — Screen On/Off Awareness (HIGH PRIORITY)
**Impact:** Eliminates ~100% of wasted wake-ups when screen is off.

**Description:**  
Create a `ScreenStateReceiver` that cancels the minute-tick alarm when the screen turns off and re-enables it (plus an immediate catch-up refresh) when the screen turns back on.

**Files to create/modify:**
- **NEW** `app/src/main/java/com/clockweather/app/receiver/ScreenStateReceiver.kt`
- **MODIFY** `app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt`

**Implementation details:**
```
1. Create ScreenStateReceiver : BroadcastReceiver
   - ACTION_SCREEN_OFF → ClockAlarmReceiver.cancelNextTick(context)
   - ACTION_SCREEN_ON  → ClockAlarmReceiver.scheduleNextTick(context)
                        + app.refreshAllWidgets(context, isClockTick = false)
                          (full refresh to catch up time & weather since screen was off)

2. In ClockWeatherApplication.onCreate():
   - Register ScreenStateReceiver dynamically via context.registerReceiver()
   - Must be dynamic — screen on/off intents CANNOT be declared in AndroidManifest since API 26

3. Guard: only register if hasAnyActiveWidgets() is true
   - Also register/unregister in BaseWidgetProvider.onEnabled()/onDisabled()
```

**Expected behavior:**
- Screen off → alarm cancelled → zero CPU wake-ups → zero battery drain from widget
- Screen on → alarm re-scheduled → immediate full refresh → time is correct within milliseconds

---

### Task 2 — Wire High-Precision Setting Into Alarm Scheduler (HIGH PRIORITY)
**Impact:** Makes the existing user-facing toggle actually work.

**Description:**  
The `isHighPrecision` variable in `ClockAlarmReceiver.scheduleNextTick()` is hardcoded to `true`. Wire it to the `KEY_HIGH_PRECISION` DataStore preference.

**Files to modify:**
- `app/src/main/java/com/clockweather/app/receiver/ClockAlarmReceiver.kt`

**Implementation details:**
```
1. Add a parameter: scheduleNextTick(context, isHighPrecision: Boolean = true)

2. In scheduleNextTick(), replace:
     val isHighPrecision = true
   with the parameter.

3. When high-precision is OFF:
   - Use AlarmManager.RTC (non-wakeup) instead of RTC_WAKEUP
   - Use setAndAllowWhileIdle() (inexact) instead of setExactAndAllowWhileIdle()
   - This means the clock updates only when the device is already awake

4. Update all callers to pass the resolved preference:
   - ClockAlarmReceiver.onReceive() — read from DataStore or accept cached value
   - BootCompletedReceiver.onReceive()
   - TimeChangedReceiver.onReceive()
   - BaseWidgetProvider.onEnabled()
   - ClockWeatherApplication.onCreate()

5. Simplest approach: keep the parameter defaulting to true,
   and callers that have DataStore access pass the resolved value.
```

**Expected behavior:**
- High-precision ON → exact wakeup alarms (current behavior, for users who want second-accurate clocks)
- High-precision OFF → non-wakeup inexact alarms → significant battery savings, clock may drift 1-2 min when screen is off

---

### Task 3 — Battery-Level Awareness (MEDIUM PRIORITY)
**Impact:** Automatic graceful degradation when battery is low.

**Description:**  
Query the battery level in the alarm scheduler and throttle/cancel updates when battery is critically low.

**Files to modify:**
- `app/src/main/java/com/clockweather/app/receiver/ClockAlarmReceiver.kt`

**Implementation details:**
```
1. In scheduleNextTick(), before scheduling the alarm:

   val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
   val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
   val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
   val batteryPct = if (scale > 0) (level * 100) / scale else 100

2. Tiered behavior:
   - Battery > 15%  → use normal scheduling (respect high-precision setting)
   - Battery 6-15%  → force RTC (non-wakeup) + inexact scheduling regardless of settings
   - Battery ≤ 5%   → cancel alarm entirely; rely on screen-on receiver (Task 1) to resume

3. Log the battery tier decision for debugging

4. When battery recovers above threshold, next screen-on or next tick naturally re-evaluates
```

**Expected behavior:**
- Normal battery → full accuracy
- Low battery → reduced wake-ups, still updates when device is awake
- Critical battery → no clock alarms at all, resumes on screen-on or charge

---

### Task 4 — Cache DataStore Preferences In-Memory (MEDIUM PRIORITY)
**Impact:** Eliminates DataStore I/O on every 60-second tick.

**Description:**  
Create an in-memory cache of widget-relevant preferences that's kept in sync via a Flow collector, so minute ticks can read preferences without disk I/O.

**Files to create/modify:**
- **NEW** `app/src/main/java/com/clockweather/app/util/WidgetPrefsCache.kt`
- **MODIFY** `app/src/main/java/com/clockweather/app/ClockWeatherApplication.kt`
- **MODIFY** `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`

**Implementation details:**
```
1. Create WidgetPrefsCache singleton:
   - Holds a volatile var snapshot: Preferences? = null
   - init(dataStore: DataStore<Preferences>, scope: CoroutineScope):
     launches a collector that updates snapshot on every emission
   - fun get(): Preferences — returns snapshot or blocks-once to seed it

2. In ClockWeatherApplication.onCreate():
   WidgetPrefsCache.init(dataStore, appScope)

3. In BaseWidgetUpdater.updateClockOnly():
   Replace: val prefs = entryPoint.dataStore().data.first()
   With:    val prefs = WidgetPrefsCache.get()

4. Keep the full DataStore.data.first() read in updateWidget() for full rebuilds
   where freshness matters more.
```

**Expected behavior:**
- Minute ticks read from RAM instead of disk
- Preferences still stay fresh (updated within seconds of user changes)
- Full rebuilds still get authoritative DataStore values

---

### Task 5 — Widen Incremental Update Window (MEDIUM PRIORITY)
**Impact:** Prevents expensive full rebuilds when a single tick is missed by 1-2 minutes (common during Doze).

**Description:**  
Currently, `WidgetClockUpdateModeResolver` only returns `INCREMENTAL` if the gap is exactly 1 minute. Widen this to 1-3 minutes so small Doze-induced delays don't trigger full weather+clock rebuilds.

**Files to modify:**
- `app/src/main/java/com/clockweather/app/presentation/widget/common/WidgetClockUpdateMode.kt`
- `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`
- `app/src/test/java/com/clockweather/app/presentation/widget/common/WidgetClockUpdateModeResolverTest.kt`

**Implementation details:**
```
1. In WidgetClockUpdateModeResolver.resolve():
   Change: currentEpochMinute - lastRenderedEpochMinute == 1L
   To:     currentEpochMinute - lastRenderedEpochMinute in 1L..3L

2. In BaseWidgetUpdater.updateClockOnly() incremental path:
   - The clock digits are already computed from LocalTime.now(), not from
     "previous minute + 1", so they'll be correct regardless of gap size
   - However, the ViewFlipper setDisplayedChild() diff logic compares
     current vs "previous minute" — this needs to compare against the
     ACTUAL last-rendered digits (store h1,h2,m1,m2 in WidgetClockStateStore)
     OR just always set all 4 digits when gap > 1

3. Update WidgetClockUpdateModeResolverTest:
   - Add test: gap of 2 → INCREMENTAL
   - Add test: gap of 3 → INCREMENTAL
   - Update test: gap of 4 → FULL
```

**Expected behavior:**
- 1-3 min gap → cheap incremental digit update (no weather, no full rebuild)
- 4+ min gap → full rebuild (likely means something unusual happened)

---

### Task 6 — Use `TextClock` for Simple-Digit Widget Variants (LOW PRIORITY / FUTURE)
**Impact:** Eliminates alarm-based time updates entirely for simple-text widget variants.

**Description:**  
Android's `TextClock` view auto-updates its display from the system clock with zero alarm overhead. For widget variants that use `usesSimpleClockDigits = true` (plain TextViews, no flip animation), replace the four digit TextViews with a single `TextClock`.

**Files to modify:**
- Layout XML files for simple-digit variants (currently none override `usesSimpleClockDigits = true`, so this is future-proofing)
- `app/src/main/java/com/clockweather/app/presentation/widget/common/WidgetDataBinder.kt`
- `app/src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt`

**Implementation details:**
```
1. In the widget layout XML, replace:
   <TextView android:id="@+id/digit_h1" ... />
   <TextView android:id="@+id/digit_h2" ... />
   <TextView android:id="@+id/colon" ... />
   <TextView android:id="@+id/digit_m1" ... />
   <TextView android:id="@+id/digit_m2" ... />

   With:
   <TextClock
       android:id="@+id/text_clock"
       android:format24Hour="HH:mm"
       android:format12Hour="hh:mm a"
       ... />

2. In WidgetDataBinder.bindSimpleClockViews():
   - Use views.setCharSequence(R.id.text_clock, "setFormat24Hour", "HH:mm")
   - Use views.setCharSequence(R.id.text_clock, "setFormat12Hour", "hh:mm a")
   - TextClock handles AM/PM automatically

3. In BaseWidgetUpdater.updateClockOnly():
   - If usesSimpleClockDigits → skip clock update entirely (TextClock handles it)

4. In ClockAlarmReceiver:
   - If ALL active widgets use TextClock → don't schedule minute alarms at all
   - Only schedule if at least one ViewFlipper widget is active
```

**Note:** This is most impactful when a user only has simple-digit widgets — in that case, the app needs ZERO minute alarms. The flip-clock ViewFlipper variants will always need alarm-based updates since ViewFlipper can't self-animate on time changes.

**Expected behavior:**
- Simple-digit widgets update time with zero battery cost (system handles it)
- Flip-clock widgets continue using alarm-based updates
- Mixed configurations only alarm for the flip-clock widgets

---

### Task 7 — Always-On Display (AOD) Handling (LOW PRIORITY)
**Impact:** Correct behavior on devices with AOD enabled.

**Description:**  
Some devices keep the screen in an "always-on" state where `isInteractive()` returns true but the display is in low-power mode. The screen-on/off receiver (Task 1) may not trigger correctly on these devices.

**Files to modify:**
- `app/src/main/java/com/clockweather/app/receiver/ScreenStateReceiver.kt` (from Task 1)

**Implementation details:**
```
1. Also listen for ACTION_DREAMING_STARTED / ACTION_DREAMING_STOPPED
   (ambient display / screensaver mode)

2. When dreaming starts → treat as screen-off (cancel alarms)
   When dreaming stops → treat as screen-on (resume alarms + catch-up)

3. Check PowerManager.isInteractive() as a fallback in scheduleNextTick()
   to double-check screen state before scheduling a wakeup alarm
```

---

## Implementation Order

```
Phase 1 (Immediate, biggest battery win):
  ✅ Task 1 — Screen On/Off Awareness
  ✅ Task 2 — Wire High-Precision Setting

Phase 2 (Next iteration):
  ✅ Task 3 — Battery-Level Awareness
  ✅ Task 4 — Cache DataStore Preferences
  ✅ Task 5 — Widen Incremental Update Window

Phase 3 (Future enhancement):
  ✅ Task 6 — TextClock for Simple Variants
  ✅ Task 7 — AOD Handling
```

## Summary of Battery Impact

| Scenario | Before | After (all tasks) |
|---|---|---|
| Screen off, normal battery | ~1 wake-up/min (1440/day) | **0 wake-ups** |
| Screen off, low battery | ~1 wake-up/min | **0 wake-ups** |
| Screen on, high-precision ON | Exact alarm every min | Same (no change needed) |
| Screen on, high-precision OFF | Exact alarm every min (bug) | Inexact non-wakeup alarm |
| Screen on, simple-digit widget | Alarm every min | **0 alarms** (TextClock) |
| Per-tick I/O | DataStore disk read | **RAM read** (cached) |
| Missed tick by 2 min | Full weather+clock rebuild | **Cheap incremental update** |

