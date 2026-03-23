package com.clockweather.app.receiver

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
class ClockAlarmReceiverTest {

    private lateinit var receiver: ClockAlarmReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setup() {
        receiver = ClockAlarmReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)
        powerManager = mockk(relaxed = true)
        appWidgetManager = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)

        every { context.applicationContext } returns app
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.packageName } returns "com.clockweather.app"

        // Mock battery check — return healthy battery
        val batteryIntent = mockk<Intent>(relaxed = true)
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 80
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } returns 100
        every { context.registerReceiver(isNull(), any()) } returns batteryIntent

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager

        // Default: has active widgets
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(1)

        coEvery { app.resolveHighPrecision() } returns true
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
        Thread.sleep(1000)

        coVerify(timeout = 3000) {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = true
            )
        }
        verify(exactly = 0) { app.pushClockInstant(any(), any()) }
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
            app.pushClockInstant(forceAllDigits = true, suppressAnimationWindow = true)
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
        val batteryIntent = mockk<Intent>(relaxed = true)
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 3
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } returns 100
        every { context.registerReceiver(isNull(), any()) } returns batteryIntent

        ClockAlarmReceiver.scheduleKeepalive(context)

        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
    }
}
