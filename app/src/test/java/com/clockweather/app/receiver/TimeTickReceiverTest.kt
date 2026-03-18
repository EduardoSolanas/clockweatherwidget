package com.clockweather.app.receiver

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [TimeTickReceiver] intent filter configuration.
 *
 * Uses Robolectric because IntentFilter requires real Android framework classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimeTickReceiverTest {

    @Test
    fun `intent filter contains ACTION_TIME_TICK`() {
        val filter = TimeTickReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_TIME_TICK))
    }

    @Test
    fun `intent filter has exactly 1 action`() {
        val filter = TimeTickReceiver.buildIntentFilter()
        assertEquals(1, filter.countActions())
    }
}
