package com.clockweather.app.receiver

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ScreenStateReceiver] intent filter configuration.
 *
 * Verifies that the receiver listens for all required screen lifecycle events.
 * Uses Robolectric because IntentFilter requires real Android framework classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenStateReceiverTest {

    @Test
    fun `intent filter contains ACTION_SCREEN_ON`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_SCREEN_ON))
    }

    @Test
    fun `intent filter contains ACTION_SCREEN_OFF`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_SCREEN_OFF))
    }

    @Test
    fun `intent filter contains ACTION_USER_PRESENT`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_USER_PRESENT))
    }

    @Test
    fun `intent filter contains ACTION_DREAMING_STARTED`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_DREAMING_STARTED))
    }

    @Test
    fun `intent filter contains ACTION_DREAMING_STOPPED`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_DREAMING_STOPPED))
    }

    @Test
    fun `intent filter contains ACTION_CLOSE_SYSTEM_DIALOGS`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue(filter.hasAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    @Test
    fun `intent filter has exactly 6 actions`() {
        val filter = ScreenStateReceiver.buildIntentFilter()
        assertTrue("Expected 6 actions", filter.countActions() == 6)
    }
}
