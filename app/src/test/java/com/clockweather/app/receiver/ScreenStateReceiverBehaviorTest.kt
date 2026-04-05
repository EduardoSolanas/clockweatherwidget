package com.clockweather.app.receiver

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral tests for [ScreenStateReceiver] — the dynamically registered screen-lifecycle handler.
 *
 * Invariants protected:
 * 1. SCREEN_OFF unregisters TIME_TICK and schedules a keepalive alarm.
 * 2. SCREEN_ON registers TIME_TICK and triggers unlock convergence only when not keyguard-locked.
 * 3. USER_PRESENT triggers unlock convergence (not throttled by prior SCREEN_ON).
 * 4. DREAMING_STARTED unregisters TIME_TICK and schedules a keepalive alarm.
 * 5. DREAMING_STOPPED registers TIME_TICK and triggers unlock convergence only when not keyguard-locked.
 * 6. Rapid SCREEN_ON events within 2500 ms are throttled (only first fires).
 * 7. USER_PRESENT bypasses throttle even when called immediately after SCREEN_ON.
 * 8. All unlock convergences use suppressAnimation=true (no visible layout flash).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenStateReceiverBehaviorTest {

    private lateinit var receiver: ScreenStateReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context
    private lateinit var keyguardManager: KeyguardManager

    @Before
    fun setup() {
        receiver = ScreenStateReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)
        keyguardManager = mockk(relaxed = true)

        every { context.applicationContext } returns app
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        every { keyguardManager.isKeyguardLocked } returns false

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleKeepalive(any()) } just Runs
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true

        coEvery { app.syncClockNow(any(), suppressAnimation = any(), reassertAfterReschedule = any()) } just Runs
        coEvery { app.resolveHighPrecision() } returns true

        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0

        // Reset throttle so tests are independent of each other.
        ScreenStateReceiver.resetUnlockConvergenceThrottleForTests()
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── SCREEN_OFF ────────────────────────────────────────────────

    @Test
    fun `SCREEN_OFF unregisters TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

        verify(exactly = 1) { app.unregisterTimeTickReceiver() }
    }

    @Test
    fun `SCREEN_OFF schedules keepalive alarm`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

        verify(exactly = 1) { ClockAlarmReceiver.scheduleKeepalive(any()) }
    }

    @Test
    fun `SCREEN_OFF does not trigger unlock convergence`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    // ── SCREEN_ON ─────────────────────────────────────────────────

    @Test
    fun `SCREEN_ON registers TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        verify(exactly = 1) { app.registerTimeTickReceiver() }
    }

    @Test
    fun `SCREEN_ON triggers unlock convergence with suppressAnimation true`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(1500)

        coVerify(timeout = 1500) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }

    @Test
    fun `SCREEN_ON while keyguard locked defers unlock convergence`() {
        every { keyguardManager.isKeyguardLocked } returns true

        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(500)

        verify(exactly = 1) { app.registerTimeTickReceiver() }
        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    // ── USER_PRESENT ──────────────────────────────────────────────

    @Test
    fun `USER_PRESENT triggers unlock convergence with suppressAnimation true`() {
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        Thread.sleep(1500)

        coVerify(timeout = 1500) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }

    // ── DREAMING_STARTED ──────────────────────────────────────────

    @Test
    fun `DREAMING_STARTED unregisters TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STARTED))

        verify(exactly = 1) { app.unregisterTimeTickReceiver() }
    }

    @Test
    fun `DREAMING_STARTED schedules keepalive alarm`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STARTED))

        verify(exactly = 1) { ClockAlarmReceiver.scheduleKeepalive(any()) }
    }

    @Test
    fun `DREAMING_STARTED does not trigger unlock convergence`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STARTED))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    // ── DREAMING_STOPPED ──────────────────────────────────────────

    @Test
    fun `DREAMING_STOPPED registers TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))

        verify(exactly = 1) { app.registerTimeTickReceiver() }
    }

    @Test
    fun `DREAMING_STOPPED triggers unlock convergence`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))

        Thread.sleep(1500)

        coVerify(timeout = 1500) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }

    @Test
    fun `DREAMING_STOPPED while keyguard locked defers unlock convergence`() {
        every { keyguardManager.isKeyguardLocked } returns true

        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))

        Thread.sleep(500)

        verify(exactly = 1) { app.registerTimeTickReceiver() }
        coVerify(exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }

    // ── Unlock convergence throttle ───────────────────────────────

    @Test
    fun `rapid SCREEN_ON events within throttle window fire only once`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON)) // within 2500 ms — throttled

        Thread.sleep(2000)

        coVerify(exactly = 1, timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }

    @Test
    fun `USER_PRESENT is never throttled even immediately after SCREEN_ON`() {
        // First SCREEN_ON arms the throttle.
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        // USER_PRESENT bypasses the throttle (throttleEnabled=false for USER_PRESENT).
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        Thread.sleep(2000)

        // Both events must fire their own convergence.
        coVerify(exactly = 2, timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }

    @Test
    fun `SCREEN_ON fires again after throttle window expires`() {
        // First call sets throttle timestamp.
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        // Reset the throttle (simulates 2500+ ms elapsing) then fire again.
        ScreenStateReceiver.resetUnlockConvergenceThrottleForTests()
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(2000)

        coVerify(exactly = 2, timeout = 2000) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = any())
        }
    }
}

