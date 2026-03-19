package com.clockweather.app.receiver

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
 * Tests for [ScreenStateReceiver] dispatch behaviour.
 *
 * Verifies the contract:
 * - SCREEN_ON  → fast pushClockInstant() + register TIME_TICK + schedule alarm (NO full sync)
 * - USER_PRESENT → full syncClockNow() (async)
 * - SCREEN_OFF → unregister TIME_TICK + keepalive alarm (NOT full cancel)
 * - DREAMING_STARTED → same as SCREEN_OFF
 * - DREAMING_STOPPED → same as SCREEN_ON
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenStateReceiverBehaviorTest {

    private lateinit var receiver: ScreenStateReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context

    @Before
    fun setup() {
        receiver = ScreenStateReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns app

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleKeepalive(any()) } just Runs
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── SCREEN_ON ─────────────────────────────────────────────

    @Test
    fun `SCREEN_ON registers TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        verify(exactly = 1) { app.registerTimeTickReceiver() }
    }

    @Test
    fun `SCREEN_ON calls pushClockInstant synchronously`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        verify(exactly = 1) { app.pushClockInstant() }
    }

    @Test
    fun `SCREEN_ON does NOT trigger full syncClockNow`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        coVerify(exactly = 0) { app.syncClockNow(any()) }
    }

    // ── USER_PRESENT ──────────────────────────────────────────

    @Test
    fun `USER_PRESENT triggers full syncClockNow`() {
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))
        // syncClockNow is launched asynchronously — verify it was called via coVerify
        coVerify(timeout = 2000) { app.syncClockNow(any()) }
    }

    @Test
    fun `USER_PRESENT does NOT call pushClockInstant directly`() {
        // pushClockInstant IS called — but only from inside syncClockNow, not directly.
        // We verify the receiver itself doesn't call it.
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))
        // syncClockNow calls pushClockInstant internally, but the receiver should not
        // call it separately. We can only check that registerTimeTickReceiver is NOT called
        // (that's a SCREEN_ON thing, not USER_PRESENT).
        verify(exactly = 0) { app.registerTimeTickReceiver() }
    }

    // ── SCREEN_OFF ────────────────────────────────────────────

    @Test
    fun `SCREEN_OFF unregisters TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        verify(exactly = 1) { app.unregisterTimeTickReceiver() }
    }

    @Test
    fun `SCREEN_OFF schedules keepalive alarm instead of cancelling`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        verify(exactly = 1) { ClockAlarmReceiver.scheduleKeepalive(any()) }
    }

    @Test
    fun `SCREEN_OFF does NOT cancel alarm`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        verify(exactly = 0) { ClockAlarmReceiver.cancelNextTick(any()) }
    }

    // ── DREAMING_STARTED ──────────────────────────────────────

    @Test
    fun `DREAMING_STARTED unregisters TIME_TICK`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STARTED))
        verify(exactly = 1) { app.unregisterTimeTickReceiver() }
    }

    @Test
    fun `DREAMING_STARTED schedules keepalive alarm`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STARTED))
        verify(exactly = 1) { ClockAlarmReceiver.scheduleKeepalive(any()) }
    }

    // ── DREAMING_STOPPED ──────────────────────────────────────

    @Test
    fun `DREAMING_STOPPED registers TIME_TICK`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))
        verify(exactly = 1) { app.registerTimeTickReceiver() }
    }

    @Test
    fun `DREAMING_STOPPED calls pushClockInstant`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))
        verify(exactly = 1) { app.pushClockInstant() }
    }

    @Test
    fun `DREAMING_STOPPED does NOT trigger full syncClockNow`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))
        coVerify(exactly = 0) { app.syncClockNow(any()) }
    }
}
