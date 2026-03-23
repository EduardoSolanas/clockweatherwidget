package com.clockweather.app.receiver

import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun `TIME_TICK triggers animated clock refresh`() {
        val receiver = TimeTickReceiver()
        val app = mockk<ClockWeatherApplication>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns app

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        coVerify(timeout = 3000) {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = true
            )
        }
    }

    @Test
    fun `TIME_TICK refresh failure falls back to instant push`() {
        val receiver = TimeTickReceiver()
        val app = mockk<ClockWeatherApplication>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns app
        coEvery {
            app.refreshAllWidgets(
                context,
                isClockTick = true,
                allowAnimation = true
            )
        } throws RuntimeException("boom")

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        verify(timeout = 3000) {
            app.pushClockInstant(forceAllDigits = true, suppressAnimationWindow = true)
        }
    }
}
