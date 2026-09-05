package com.clockweather.app.presentation.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRefreshGateTest {
    @Test
    fun `in-flight refresh is rejected and failed refresh can retry`() {
        val gate = ManualRefreshGate(300_000L)

        assertTrue(gate.tryAcquire(1_000L))
        assertFalse(gate.tryAcquire(1_001L))
        gate.finish(succeeded = false, nowMs = 1_001L)
        assertTrue(gate.tryAcquire(1_001L))
    }

    @Test
    fun `successful refresh applies cooldown`() {
        val gate = ManualRefreshGate(300_000L)

        assertTrue(gate.tryAcquire(1_000L))
        gate.finish(succeeded = true, nowMs = 1_000L)

        assertFalse(gate.tryAcquire(2_000L))
        assertTrue(gate.tryAcquire(301_001L))
    }
}
