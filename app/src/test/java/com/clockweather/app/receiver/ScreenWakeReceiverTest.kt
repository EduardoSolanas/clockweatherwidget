package com.clockweather.app.receiver

import android.content.Context
import android.content.Intent
import com.clockweather.app.worker.WeatherUpdateScheduler
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class ScreenWakeReceiverTest {

    private val context: Context = mockk()
    private val receiver = ScreenWakeReceiver()

    @Before
    fun mockScheduler() {
        mockkObject(WeatherUpdateScheduler)
        justRun { WeatherUpdateScheduler.scheduleImmediateRefresh(any()) }
    }

    @After
    fun unmockScheduler() {
        unmockkObject(WeatherUpdateScheduler)
    }

    private fun intentWithAction(action: String?): Intent =
        mockk { every { this@mockk.action } returns action }

    @Test
    fun `screen on enqueues a freshness-gated refresh`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_SCREEN_ON))

        verify(exactly = 1) { WeatherUpdateScheduler.scheduleImmediateRefresh(context) }
    }

    @Test
    fun `unlock enqueues a freshness-gated refresh`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_USER_PRESENT))

        verify(exactly = 1) { WeatherUpdateScheduler.scheduleImmediateRefresh(context) }
    }

    @Test
    fun `unrelated action does nothing`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_SCREEN_OFF))

        verify(exactly = 0) { WeatherUpdateScheduler.scheduleImmediateRefresh(any()) }
    }

    @Test
    fun `null action does nothing`() {
        receiver.onReceive(context, intentWithAction(null))

        verify(exactly = 0) { WeatherUpdateScheduler.scheduleImmediateRefresh(any()) }
    }
}
