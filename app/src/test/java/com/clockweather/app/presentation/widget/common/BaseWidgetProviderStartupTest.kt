package com.clockweather.app.presentation.widget.common

import android.content.Context
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.receiver.ClockAlarmReceiver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BaseWidgetProviderStartupTest {

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `onEnabled registers receivers and schedules next tick`() {
        val provider = CompactWidgetProvider()
        val context = mockk<Context>()
        val app = mockk<ClockWeatherApplication>()
        every { context.applicationContext } returns app
        every { app.registerScreenStateReceiver() } just Runs
        every { app.registerTimeTickReceiver() } just Runs
        coEvery { app.resolveHighPrecision() } returns true

        mockkObject(ClockAlarmReceiver.Companion)
        every { ClockAlarmReceiver.scheduleNextTick(any(), any()) } returns Unit

        provider.onEnabled(context)
        Thread.sleep(500)

        verify(exactly = 1) { app.registerScreenStateReceiver() }
        verify(exactly = 1) { app.registerTimeTickReceiver() }
        verify(timeout = 3000) { ClockAlarmReceiver.scheduleNextTick(context, true) }
    }
}

