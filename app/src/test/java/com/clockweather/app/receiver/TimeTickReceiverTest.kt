package com.clockweather.app.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import com.clockweather.app.ClockWeatherApplication
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [TimeTickReceiver] — the primary per-minute clock-tick source.
 *
 * Invariants protected:
 * 1. Intent filter contains exactly ACTION_TIME_TICK.
 * 2. Non-TIME_TICK intents are silently ignored (no widget refresh).
 * 3. TIME_TICK records the observed epoch minute SYNCHRONOUSLY (before the coroutine).
 * 4. TIME_TICK triggers a full widget refresh with isClockTick=true, allowAnimation=true.
 * 5. TIME_TICK re-anchors the backup alarm to the system minute boundary.
 * 6. Refresh failure falls back to pushClockInstant (forceAllDigits=true).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimeTickReceiverTest {

    private lateinit var receiver: TimeTickReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context

    @Before
    fun setup() {
        receiver = TimeTickReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { context.applicationContext } returns app
        coEvery { app.refreshAllWidgets(any(), any(), any()) } just Runs
        coEvery { app.resolveHighPrecision() } returns true

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just Runs

        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ── Intent filter ─────────────────────────────────────────────

    @Test
    fun `intent filter contains ACTION_TIME_TICK`() {
        assertTrue(TimeTickReceiver.buildIntentFilter().hasAction(Intent.ACTION_TIME_TICK))
    }

    @Test
    fun `intent filter has exactly one action`() {
        assertEquals(1, TimeTickReceiver.buildIntentFilter().countActions())
    }

    // ── Action guard ──────────────────────────────────────────────

    @Test
    fun `onReceive ignores non TIME_TICK actions`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.refreshAllWidgets(any(), any(), any()) }
        verify(exactly = 0) { app.markTimeTickObserved(any()) }
    }

    @Test
    fun `onReceive ignores ACTION_TIME_CHANGED`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.refreshAllWidgets(any(), any(), any()) }
    }

    // ── Synchronous minute observation ────────────────────────────

    @Test
    fun `onReceive TIME_TICK marks current epoch minute synchronously before coroutine`() {
        val expectedMinute = System.currentTimeMillis() / 60000L

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        // markTimeTickObserved is called synchronously — verify without any sleep.
        verify(exactly = 1) {
            app.markTimeTickObserved(match { it >= expectedMinute - 1 && it <= expectedMinute + 1 })
        }
    }

    // ── Async refresh ─────────────────────────────────────────────

    @Test
    fun `onReceive TIME_TICK calls refreshAllWidgets with isClockTick true and allowAnimation true`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(3500)

        coVerify(timeout = 3000) {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = true
            )
        }
    }

    @Test
    fun `onReceive TIME_TICK re-anchors backup alarm after refresh`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(3500)

        verify(timeout = 3000) { ClockAlarmReceiver.scheduleNextTick(any(), any()) }
    }

    // ── Failure path ──────────────────────────────────────────────

    @Test
    fun `onReceive TIME_TICK refresh failure falls back to pushClockInstant with forceAllDigits`() {
        coEvery {
            app.refreshAllWidgets(context, isClockTick = true, allowAnimation = true)
        } throws RuntimeException("simulated refresh failure")

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        verify(timeout = 1500) {
            app.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true
            )
        }
    }
}

