package com.clockweather.app.receiver

import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.worker.WeatherUpdateScheduler
import io.mockk.coEvery
import io.mockk.coVerify
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

    private val application: ClockWeatherApplication = mockk(relaxed = true)
    private val context: Context = mockk {
        every { applicationContext } returns application
    }
    private val receiver = ScreenWakeReceiver()

    @Before
    fun mockScheduler() {
        ScreenWakeReceiver.lastScreenOnRedrawMillis = 0L
        mockkObject(WeatherUpdateScheduler)
        justRun { WeatherUpdateScheduler.scheduleImmediateRefresh(any()) }
        coEvery { application.refreshAllWidgets(any()) } returns Unit
    }

    @After
    fun unmockScheduler() {
        unmockkObject(WeatherUpdateScheduler)
    }

    private fun intentWithAction(action: String?): Intent =
        mockk { every { this@mockk.action } returns action }

    @Test
    fun `screen on redraws cached widgets and enqueues freshness-gated refresh`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_SCREEN_ON))

        Thread.sleep(100)

        coVerify(exactly = 1) { application.refreshAllWidgets(context) }
        verify(exactly = 1) { WeatherUpdateScheduler.scheduleImmediateRefresh(context) }
    }

    @Test
    fun `rapid duplicate screen on events within 60s are throttled to single redraw`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_SCREEN_ON))
        receiver.onReceive(context, intentWithAction(Intent.ACTION_SCREEN_ON))

        Thread.sleep(100)

        coVerify(exactly = 1) { application.refreshAllWidgets(context) }
        verify(exactly = 2) { WeatherUpdateScheduler.scheduleImmediateRefresh(context) }
    }

    @Test
    fun `unlock always redraws cached widgets and enqueues freshness-gated refresh`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_USER_PRESENT))

        Thread.sleep(100)

        coVerify(atLeast = 1) { application.refreshAllWidgets(context) }
        verify(exactly = 1) { WeatherUpdateScheduler.scheduleImmediateRefresh(context) }
    }

    @Test
    fun `unrelated action does nothing`() {
        receiver.onReceive(context, intentWithAction(Intent.ACTION_SCREEN_OFF))

        Thread.sleep(50)

        coVerify(exactly = 0) { application.refreshAllWidgets(any()) }
        verify(exactly = 0) { WeatherUpdateScheduler.scheduleImmediateRefresh(any()) }
    }

    @Test
    fun `null action does nothing`() {
        receiver.onReceive(context, intentWithAction(null))

        Thread.sleep(50)

        coVerify(exactly = 0) { application.refreshAllWidgets(any()) }
        verify(exactly = 0) { WeatherUpdateScheduler.scheduleImmediateRefresh(any()) }
    }
}
