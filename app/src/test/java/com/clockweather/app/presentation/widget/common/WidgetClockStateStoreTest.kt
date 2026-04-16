package com.clockweather.app.presentation.widget.common

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetClockStateStoreTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearState() {
        WidgetClockStateStore.clearWidget(context, 1)
        WidgetClockStateStore.clearWidget(context, 2)
    }

    // ── Epoch minute storage ──────────────────────────────────

    @Test
    fun `returns null when widget has never rendered`() {
        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
    }

    @Test
    fun `stores render minute per widget id`() {
        WidgetClockStateStore.markRendered(context, 1, 1234L)
        WidgetClockStateStore.markRendered(context, 2, 5678L)

        assertEquals(1234L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
        assertEquals(5678L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 2))
    }

    @Test
    fun `clearing one widget does not affect others`() {
        WidgetClockStateStore.markRendered(context, 1, 1234L)
        WidgetClockStateStore.markRendered(context, 2, 5678L)

        WidgetClockStateStore.clearWidget(context, 1)

        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
        assertEquals(5678L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 2))
    }

    // ── Digit state storage ───────────────────────────────────

    @Test
    fun `getLastDigits returns null when never stored`() {
        assertNull(WidgetClockStateStore.getLastDigits(context, 1))
    }

    @Test
    fun `saveLastDigits then getLastDigits round-trips correctly`() {
        val digits = DigitState(1, 4, 3, 7)
        WidgetClockStateStore.saveLastDigits(context, 1, digits)

        assertEquals(digits, WidgetClockStateStore.getLastDigits(context, 1))
    }

    @Test
    fun `digit state is per-widget-id`() {
        WidgetClockStateStore.saveLastDigits(context, 1, DigitState(1, 0, 3, 0))
        WidgetClockStateStore.saveLastDigits(context, 2, DigitState(2, 3, 5, 9))

        assertEquals(DigitState(1, 0, 3, 0), WidgetClockStateStore.getLastDigits(context, 1))
        assertEquals(DigitState(2, 3, 5, 9), WidgetClockStateStore.getLastDigits(context, 2))
    }

    // ── clearDigits (the key method for partial-vs-full logic) ─

    @Test
    fun `clearDigits removes digit state but preserves epoch minute`() {
        WidgetClockStateStore.saveLastDigits(context, 1, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.markRendered(context, 1, 9999L)

        WidgetClockStateStore.clearDigits(context, 1)

        // Digits gone → next updateWidget treats as first render
        assertNull(WidgetClockStateStore.getLastDigits(context, 1))
        // Epoch minute preserved — unrelated concern
        assertEquals(9999L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
    }

    @Test
    fun `clearDigits does not affect other widget ids`() {
        WidgetClockStateStore.saveLastDigits(context, 1, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.saveLastDigits(context, 2, DigitState(2, 3, 5, 9))

        WidgetClockStateStore.clearDigits(context, 1)

        assertNull(WidgetClockStateStore.getLastDigits(context, 1))
        assertEquals(DigitState(2, 3, 5, 9), WidgetClockStateStore.getLastDigits(context, 2))
    }

    @Test
    fun `clearDigits does not affect baseline ready state`() {
        WidgetClockStateStore.markBaselineReady(context, 1)
        WidgetClockStateStore.saveLastDigits(context, 1, DigitState(1, 4, 3, 7))

        WidgetClockStateStore.clearDigits(context, 1)

        // Baseline still ready — clearDigits only touches digit keys
        assertTrue(WidgetClockStateStore.isBaselineReady(context, 1))
    }

    @Test
    fun `clearWidget removes everything including digits and baseline`() {
        WidgetClockStateStore.saveLastDigits(context, 1, DigitState(1, 4, 3, 7))
        WidgetClockStateStore.markRendered(context, 1, 9999L)
        WidgetClockStateStore.markBaselineReady(context, 1)

        WidgetClockStateStore.clearWidget(context, 1)

        assertNull(WidgetClockStateStore.getLastDigits(context, 1))
        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
        assertFalse(WidgetClockStateStore.isBaselineReady(context, 1))
    }

    // ── Baseline ready ────────────────────────────────────────

    @Test
    fun `baseline not ready by default`() {
        assertFalse(WidgetClockStateStore.isBaselineReady(context, 1))
    }

    @Test
    fun `markBaselineReady then isBaselineReady returns true`() {
        WidgetClockStateStore.markBaselineReady(context, 1)
        assertTrue(WidgetClockStateStore.isBaselineReady(context, 1))
    }

    // ── Animation suppression window ──────────────────────────

    @Test
    fun `shouldSuppressAnimation returns true when within suppression window`() {
        val currentMinute = 1000L
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(context, 1, currentMinute + 1L)
        assertTrue(WidgetClockStateStore.shouldSuppressAnimation(context, 1, currentMinute))
    }

    @Test
    fun `shouldSuppressAnimation returns false and clears key when window has expired`() {
        val currentMinute = 1000L
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(context, 1, currentMinute - 1L)
        assertFalse(WidgetClockStateStore.shouldSuppressAnimation(context, 1, currentMinute))
        // Key removed — second call must not suppress either
        assertFalse(WidgetClockStateStore.shouldSuppressAnimation(context, 1, currentMinute))
    }

    @Test
    fun `shouldSuppressAnimation self-heals far-future orphaned window and returns false`() {
        val currentMinute = System.currentTimeMillis() / 60000L
        // Simulate a process-death orphan: window set 100 years into the future
        val orphanedFuture = currentMinute + 100L * 365 * 24 * 60
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(context, 1, orphanedFuture)

        assertFalse(WidgetClockStateStore.shouldSuppressAnimation(context, 1, currentMinute))
        // Key must be cleared so a second call also returns false
        assertFalse(WidgetClockStateStore.shouldSuppressAnimation(context, 1, currentMinute))
    }

    @Test
    fun `shouldSuppressAnimation does not clamp window that is exactly at max allowed distance`() {
        val currentMinute = 1000L
        // Exactly MAX_SUPPRESS_WINDOW_MINUTES ahead — still within allowed range
        WidgetClockStateStore.markNoAnimationUntilEpochMinute(context, 1, currentMinute + 5L)
        assertTrue(WidgetClockStateStore.shouldSuppressAnimation(context, 1, currentMinute))
    }
}
