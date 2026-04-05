package com.clockweather.app.receiver

import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [UnlockSyncReceiver] — manifest-level unlock fallback.
 *
 * Invariants protected:
 * 1. Only ACTION_USER_PRESENT and ACTION_SCREEN_ON trigger sync; all others are ignored.
 * 2. No active widgets → sync is skipped entirely.
 * 3. The receiver skips duplicate work when the live dynamic receiver is already active.
 * 4. ACTION_USER_PRESENT syncs with reassertAfterReschedule=false (unlock path).
 * 5. ACTION_SCREEN_ON syncs only when the device is already unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UnlockSyncReceiverTest {

    private lateinit var receiver: UnlockSyncReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var keyguardManager: KeyguardManager

    @Before
    fun setup() {
        receiver = UnlockSyncReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)
        appWidgetManager = mockk()
        keyguardManager = mockk(relaxed = true)

        every { context.applicationContext } returns app
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        every { keyguardManager.isKeyguardLocked } returns false

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(1)

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
        every { app.isScreenStateReceiverRegistered() } returns false

        coEvery { app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any()) } just Runs
        coEvery { app.resolveHighPrecision() } returns true
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── Action guard ───────────────────────────────────────────────

    @Test
    fun `onReceive ignores ACTION_SCREEN_OFF`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    @Test
    fun `onReceive ignores ACTION_TIME_TICK`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    @Test
    fun `onReceive ignores unrelated intents`() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    // ── No-widget short-circuit ────────────────────────────────────

    @Test
    fun `onReceive USER_PRESENT skips sync when no active widgets`() {
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns false

        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        Thread.sleep(500)

        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
        verify(exactly = 0) { app.registerScreenStateReceiver() }
    }

    // ── USER_PRESENT ───────────────────────────────────────────────

    @Test
    fun `onReceive USER_PRESENT registers receivers then calls syncClockNow`() {
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        Thread.sleep(2500)

        verify(timeout = 2000) { app.registerScreenStateReceiver() }
        verify(timeout = 2000) { app.registerTimeTickReceiver() }
        coVerify(timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = false)
        }
    }

    @Test
    fun `onReceive USER_PRESENT skips duplicate fallback when dynamic receiver is already active`() {
        every { app.isScreenStateReceiverRegistered() } returns true

        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        Thread.sleep(500)

        verify(exactly = 0) { app.registerTimeTickReceiver() }
        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    @Test
    fun `onReceive USER_PRESENT passes reassertAfterReschedule false`() {
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        Thread.sleep(2500)

        coVerify(timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = false)
        }
        // Must NOT be called with reassertAfterReschedule=true
        coVerify(exactly = 0) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = true)
        }
    }

    // ── SCREEN_ON ─────────────────────────────────────────────────

    @Test
    fun `onReceive SCREEN_ON registers receivers then calls syncClockNow when already unlocked`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(2500)

        verify(timeout = 2000) { app.registerScreenStateReceiver() }
        verify(timeout = 2000) { app.registerTimeTickReceiver() }
        coVerify(timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }

    @Test
    fun `onReceive SCREEN_ON passes reassertAfterReschedule true`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(2500)

        // SCREEN_ON is not USER_PRESENT, so reassertAfterReschedule = true
        coVerify(timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = true)
        }
    }

    @Test
    fun `onReceive SCREEN_ON while keyguard locked registers receivers but skips sync`() {
        every { keyguardManager.isKeyguardLocked } returns true

        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(500)

        verify(exactly = 1) { app.registerScreenStateReceiver() }
        verify(exactly = 1) { app.registerTimeTickReceiver() }
        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
        verify(exactly = 0) { ClockAlarmReceiver.scheduleNextTick(any(), any()) }
    }
}

