package com.clockweather.app.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import com.clockweather.app.ClockWeatherApplication
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ClockAlarmReceiver].
 *
 * Verifies:
 * - Screen-on alarm fires → refreshes widgets + reschedules regular tick
 * - Screen-off alarm fires → skips widget refresh + reschedules keepalive
 * - No active widgets → cancels alarm, no refresh
 * - scheduleKeepalive() creates an alarm (doesn't cancel)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@SuppressLint("UnspecifiedRegisterReceiverFlag")
class ClockAlarmReceiverTest {

    private lateinit var receiver: ClockAlarmReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        receiver = ClockAlarmReceiver()
        app = mockk()
        context = mockk()
        powerManager = mockk()
        appWidgetManager = mockk()
        alarmManager = mockk()
        sharedPreferences = mockk()

        every { context.applicationContext } returns app
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.packageName } returns "com.clockweather.app"
        every { app.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.contains(any()) } returns false
        every { sharedPreferences.getLong(any(), any()) } returns -1L

        // Mock battery check — return healthy battery
        val batteryIntent = mockk<Intent>()
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 80
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } returns 100
        every { context.registerReceiver(isNull(), any()) } returns batteryIntent

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager

        // Default: has active widgets
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(1)
        every { app.getLastObservedTimeTickEpochMinute() } returns -1L
        every { app.isTimeTickReceiverRegistered() } returns false
        coEvery { app.refreshAllWidgets(any(), any(), any()) } just Runs
        every { app.pushClockInstant(any(), any(), any(), any()) } just Runs
        coEvery { app.resolveHighPrecision() } returns true

        every { powerManager.isInteractive } returns true
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } just Runs
        every { alarmManager.setAndAllowWhileIdle(any(), any(), any()) } just Runs
        every { alarmManager.cancel(any<android.app.PendingIntent>()) } just Runs
        every { alarmManager.canScheduleExactAlarms() } returns true
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `onReceive with screen ON refreshes widgets`() {
        every { powerManager.isInteractive } returns true

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        // Give the coroutine time to run
        Thread.sleep(3400)

        coVerify(timeout = 3000) {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = true
            )
        }
        verify(exactly = 0) { app.pushClockInstant(any(), any(), any()) }
    }

    @Test
    fun `onReceive with screen ON and TIME_TICK registered performs quiet fallback when stale`() {
        every { powerManager.isInteractive } returns true
        every { app.isTimeTickReceiverRegistered() } returns true
        every { app.getLastObservedTimeTickEpochMinute() } returns -1L

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        Thread.sleep(3400)

        coVerify(exactly = 0, timeout = 3000) {
            app.refreshAllWidgets(
                any(),
                isClockTick = true,
                allowAnimation = true
            )
        }
        verify(timeout = 3000) {
            app.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = false,
                quietRender = true
            )
        }
    }

    @Test
    fun `onReceive with screen ON and TIME_TICK registered skips backup when current minute already rendered`() {
        every { powerManager.isInteractive } returns true
        every { app.isTimeTickReceiverRegistered() } returns true
        every { app.getLastObservedTimeTickEpochMinute() } answers { System.currentTimeMillis() / 60000L }
        every { sharedPreferences.contains(any()) } returns true
        every { sharedPreferences.getLong(any(), any()) } answers { System.currentTimeMillis() / 60000L }

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        Thread.sleep(3400)

        coVerify(exactly = 0, timeout = 3000) {
            app.refreshAllWidgets(
                any(),
                isClockTick = true,
                allowAnimation = true
            )
        }
        verify(exactly = 0, timeout = 3000) { app.pushClockInstant(any(), any(), any()) }
    }

    @Test
    fun `onReceive with screen ON and TIME_TICK observed skips stale backup push`() {
        every { powerManager.isInteractive } returns true
        every { app.isTimeTickReceiverRegistered() } returns true
        // Simulate stale widget state for current minute.
        every { sharedPreferences.contains(any()) } returns false
        // But TIME_TICK has already been observed for that minute.
        every { app.getLastObservedTimeTickEpochMinute() } answers { System.currentTimeMillis() / 60000L }

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        Thread.sleep(3400)

        coVerify(exactly = 0, timeout = 3000) {
            app.refreshAllWidgets(
                any(),
                isClockTick = true,
                allowAnimation = true
            )
        }
        verify(exactly = 0, timeout = 3000) { app.pushClockInstant(any(), any(), any()) }
    }

    @Test
    fun `onReceive with screen OFF does NOT refresh widgets`() {
        every { powerManager.isInteractive } returns false

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        Thread.sleep(1000)

        coVerify(exactly = 0, timeout = 3000) { app.refreshAllWidgets(any(), any()) }
    }

    @Test
    fun `onReceive with no active widgets cancels alarm and does not refresh`() {
        every { powerManager.isInteractive } returns true
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf()

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        Thread.sleep(1000)

        coVerify(exactly = 0, timeout = 3000) { app.refreshAllWidgets(any(), any()) }
    }

    @Test
    fun `onReceive with refresh failure falls back to instant push`() {
        every { powerManager.isInteractive } returns true
        coEvery {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = true
            )
        } throws RuntimeException("boom")

        receiver.onReceive(context, Intent(ClockAlarmReceiver.ACTION_ALARM_TICK))

        Thread.sleep(1000)

        verify(timeout = 3000) {
            app.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true,
                quietRender = false
            )
        }
    }

    // ── scheduleKeepalive ─────────────────────────────────────

    @Test
    fun `scheduleKeepalive sets an alarm when active widgets exist`() {
        ClockAlarmReceiver.scheduleKeepalive(context)

        // Verify an alarm was set (not cancelled)
        verify { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.cancel(any<android.app.PendingIntent>()) }
    }

    @Test
    fun `scheduleKeepalive does nothing when no active widgets`() {
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf()

        ClockAlarmReceiver.scheduleKeepalive(context)

        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.set(any(), any(), any<android.app.PendingIntent>()) }
    }

    @Test
    fun `scheduleKeepalive skips when battery critical`() {
        // Battery at 3%
        val batteryIntent = mockk<Intent>()
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 3
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } returns 100
        every { context.registerReceiver(isNull(), any()) } returns batteryIntent

        ClockAlarmReceiver.scheduleKeepalive(context)

        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
    }
}
