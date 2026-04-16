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
 * 4. TIME_TICK prefers a quiet instant digit push once widget baselines are ready.
 * 5. TIME_TICK falls back to the clock-only widget refresh path while baselines are missing.
 * 6. TIME_TICK re-anchors the backup alarm to the system minute boundary.
 * 7. Failure falls back to pushClockInstant (forceAllDigits=true, quietRender=true).
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
        every { app.isCurrentMinuteFullyRendered(any()) } returns false
        every { app.areAllActiveWidgetBaselinesReady() } returns true
        every { app.getAndMarkTimeTickObserved(any()) } returns -1L  // default: no prior tick (first tick → gap)
        coEvery { app.withClockMutex(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[0] as suspend () -> Unit)()
        }
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

    // ── Mutex guard ───────────────────────────────────────────────

    @Test
    fun `TIME_TICK baseline check and push run inside withClockMutex`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        coVerify(timeout = 1500) { app.withClockMutex(any()) }
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

    // ── Atomic gap detection ──────────────────────────────────────

    @Test
    fun `onReceive TIME_TICK uses atomic getAndMarkTimeTickObserved instead of separate read-then-write`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        // getAndMarkTimeTickObserved must be called (atomic get+set)
        verify(exactly = 1) {
            app.getAndMarkTimeTickObserved(match { it >= System.currentTimeMillis() / 60000L - 1 })
        }
        // getLastObservedTimeTickEpochMinute must NOT be used directly by TimeTickReceiver
        verify(exactly = 0) { app.getLastObservedTimeTickEpochMinute() }
    }

    // ── Action guard ──────────────────────────────────────────────

    @Test
    fun `onReceive ignores non TIME_TICK actions`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.refreshAllWidgets(any(), any(), any()) }
    }

    @Test
    fun `onReceive ignores ACTION_TIME_CHANGED`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        Thread.sleep(300)

        coVerify(exactly = 0) { app.refreshAllWidgets(any(), any(), any()) }
    }

    // ── Synchronous minute observation ────────────────────────────

    @Test
    fun `onReceive TIME_TICK records current epoch minute synchronously before coroutine`() {
        val expectedMinute = System.currentTimeMillis() / 60000L

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        // getAndMarkTimeTickObserved is called synchronously — verify without any sleep.
        verify(exactly = 1) {
            app.getAndMarkTimeTickObserved(match { it >= expectedMinute - 1 && it <= expectedMinute + 1 })
        }
    }

    // ── Async refresh ─────────────────────────────────────────────

    @Test
    fun `onReceive TIME_TICK uses quiet instant push when baselines are ready`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        // Atomic getAndSet returns previous value; simulate no gap (prev = current - 1)
        every { app.getAndMarkTimeTickObserved(currentMinute) } returns currentMinute - 1L

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        verify(timeout = 1500) {
            app.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true,
                source = "TIME_TICK"
            )
        }
        coVerify(exactly = 0) { app.refreshAllWidgets(any(), any(), any()) }
    }

    @Test
    fun `onReceive TIME_TICK forces all digits when process was frozen (gap in observed minutes)`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        // Atomic getAndSet returns previous value; simulate gap (process frozen 5 min)
        every { app.getAndMarkTimeTickObserved(currentMinute) } returns currentMinute - 5L

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        verify(timeout = 1500) {
            app.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true,
                quietRender = true,
                source = "TIME_TICK"
            )
        }
    }

    @Test
    fun `onReceive TIME_TICK falls back to clock-only refresh when baselines are missing`() {
        every { app.areAllActiveWidgetBaselinesReady() } returns false

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(3500)

        coVerify(timeout = 3000) {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = false
            )
        }
        verify(exactly = 0) {
            app.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true
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
    fun `onReceive TIME_TICK skips push when current minute already fully rendered by alarm backup`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        every { app.isCurrentMinuteFullyRendered(currentMinute) } returns true
        every { app.getAndMarkTimeTickObserved(currentMinute) } returns currentMinute - 1L

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        verify(exactly = 0) {
            app.pushClockInstant(any(), any(), any())
        }
        coVerify(exactly = 0) { app.refreshAllWidgets(any(), any(), any()) }
        // Alarm re-anchor still fires even when push is skipped
        verify(timeout = 1500) { ClockAlarmReceiver.scheduleNextTick(any(), any()) }
    }

    @Test
    fun `onReceive TIME_TICK pushes normally when current minute not yet rendered`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        every { app.getAndMarkTimeTickObserved(currentMinute) } returns currentMinute - 1L
        every { app.isCurrentMinuteFullyRendered(currentMinute) } returns false

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        verify(timeout = 1500) {
            app.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true,
                source = "TIME_TICK"
            )
        }
    }

    @Test
    fun `onReceive TIME_TICK quiet path failure falls back to pushClockInstant with forceAllDigits`() {
        every {
            app.pushClockInstant(
                forceAllDigits = false,
                suppressAnimationWindow = true,
                quietRender = true,
                source = "TIME_TICK"
            )
        } throws RuntimeException("simulated refresh failure")

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        Thread.sleep(1500)

        verify(timeout = 1500) {
            app.pushClockInstant(
                forceAllDigits = true,
                suppressAnimationWindow = true,
                quietRender = true,
                source = any()
            )
        }
    }
}

