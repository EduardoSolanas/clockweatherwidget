package com.clockweather.app.receiver

import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import io.mockk.coEvery
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
class UnlockSyncReceiverTest {

    private lateinit var receiver: UnlockSyncReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context

    @Before
    fun setup() {
        receiver = UnlockSyncReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { context.applicationContext } returns app

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns true
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } just runs
        coEvery { app.resolveHighPrecision() } returns true
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `USER_PRESENT triggers quiet no-animation sync and alarm reschedule`() {
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        coVerify(timeout = 5000) { app.syncClockNow(context, true, false) }
        coVerify(timeout = 5000, exactly = 0) { app.refreshAllWidgets(any(), true, true) }
        verify(timeout = 5000) { app.registerTimeTickReceiver() }
        verify(timeout = 5000) { app.registerScreenStateReceiver() }
        verify(timeout = 5000) { ClockAlarmReceiver.scheduleNextTick(context, true) }
        verify(timeout = 3000, exactly = 0) { app.pushClockInstant(any(), any()) }
    }

    @Test
    fun `SCREEN_ON triggers quiet no-animation sync and alarm reschedule`() {
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        coVerify(timeout = 5000) { app.syncClockNow(context, true, true) }
        verify(timeout = 5000) { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }

    @Test
    fun `does nothing when no active widgets`() {
        every { ClockAlarmReceiver.hasAnyActiveWidgets(any()) } returns false

        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        coVerify(exactly = 0, timeout = 2000) { app.syncClockNow(any(), any(), any()) }
        coVerify(exactly = 0, timeout = 2000) { app.refreshAllWidgets(any(), any(), any()) }
        verify(exactly = 0, timeout = 2000) { ClockAlarmReceiver.scheduleNextTick(any(), any()) }
    }
}
