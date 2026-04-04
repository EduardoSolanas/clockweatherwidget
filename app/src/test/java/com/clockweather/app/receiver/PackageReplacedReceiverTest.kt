package com.clockweather.app.receiver

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.clockweather.app.ClockWeatherApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
class PackageReplacedReceiverTest {

    private lateinit var receiver: PackageReplacedReceiver
    private lateinit var app: ClockWeatherApplication
    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setup() {
        receiver = PackageReplacedReceiver()
        app = mockk(relaxed = true)
        context = mockk(relaxed = true)
        appWidgetManager = mockk()

        every { context.applicationContext } returns app

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(1)

        coEvery { app.syncClockNow(context, suppressAnimation = true, reassertAfterReschedule = true) } returns Unit
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `my package replaced triggers immediate widget recovery`() {
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        Thread.sleep(1000)

        verify(timeout = 1000) { app.registerScreenStateReceiver() }
        verify(timeout = 1000) { app.registerTimeTickReceiver() }
        coVerify(timeout = 1000) {
            app.syncClockNow(context, suppressAnimation = true, reassertAfterReschedule = true)
        }
    }

    @Test
    fun `my package replaced does nothing when no widgets are active`() {
        every { appWidgetManager.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf()

        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        Thread.sleep(250)

        verify(exactly = 0) { app.registerScreenStateReceiver() }
        verify(exactly = 0) { app.registerTimeTickReceiver() }
        coVerify(exactly = 0) {
            app.syncClockNow(any(), suppressAnimation = true, reassertAfterReschedule = true)
        }
    }
}