package com.clockweather.app.receiver

import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenStateReceiverBehaviorTest {

    private lateinit var receiver: ScreenStateReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context

    @Before
    fun setup() {
        ScreenStateReceiver.resetUnlockConvergenceThrottleForTests()
        receiver = ScreenStateReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns app

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleKeepalive(any()) } just runs
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `SCREEN_ON registers TIME_TICK receiver`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        verify(exactly = 1) { app.registerTimeTickReceiver() }
    }

    @Test
    fun `SCREEN_ON runs quiet no-animation sync`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        coVerify(timeout = 3000) { app.syncClockNow(any(), suppressAnimation = true) }
        verify(timeout = 3000, exactly = 0) { app.pushClockInstant(any(), any(), any()) }
    }

    @Test
    fun `USER_PRESENT runs quiet no-animation sync`() {
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))
        coVerify(timeout = 3000) { app.syncClockNow(any(), true, false) }
        coVerify(timeout = 3000, exactly = 0) { app.refreshAllWidgets(any(), true, true) }
    }

    @Test
    fun `SCREEN_ON followed by USER_PRESENT runs two quiet syncs`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        coVerify(timeout = 6000, exactly = 1) { app.syncClockNow(any(), true, true) }
        coVerify(timeout = 6000, exactly = 1) { app.syncClockNow(any(), true, false) }
        coVerify(timeout = 6000, exactly = 0) { app.refreshAllWidgets(any(), true, true) }
    }

    @Test
    fun `SCREEN_OFF unregisters TIME_TICK and schedules keepalive`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        verify(exactly = 1) { app.unregisterTimeTickReceiver() }
        verify(exactly = 1) { ClockAlarmReceiver.scheduleKeepalive(any()) }
    }

    @Test
    fun `DREAMING_STARTED unregisters TIME_TICK and schedules keepalive`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STARTED))
        verify(exactly = 1) { app.unregisterTimeTickReceiver() }
        verify(exactly = 1) { ClockAlarmReceiver.scheduleKeepalive(any()) }
    }

    @Test
    fun `DREAMING_STOPPED registers TIME_TICK and runs quiet sync`() {
        receiver.onReceive(context, Intent(Intent.ACTION_DREAMING_STOPPED))
        verify(exactly = 1) { app.registerTimeTickReceiver() }
        coVerify(timeout = 3000) { app.syncClockNow(any(), suppressAnimation = true) }
    }

    @Test
    fun `CLOSE_SYSTEM_DIALOGS with homekey runs quiet sync without reassert`() {
        receiver.onReceive(
            context,
            Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).apply { putExtra("reason", "homekey") }
        )

        coVerify(timeout = 3000) { app.syncClockNow(any(), true, false) }
    }

    @Test
    fun `CLOSE_SYSTEM_DIALOGS without homekey does nothing`() {
        receiver.onReceive(
            context,
            Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).apply { putExtra("reason", "recentapps") }
        )

        coVerify(timeout = 3000, exactly = 0) { app.syncClockNow(any(), any(), any()) }
    }
}
