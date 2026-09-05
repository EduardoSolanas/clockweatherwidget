package com.clockweather.app.receiver

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.clockweather.app.worker.WeatherUpdateScheduler
import com.clockweather.app.worker.WeatherUpdateWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BootCompletedReceiverWorkTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `boot restores periodic schedule and enqueues immediate freshness check when widgets exist`() = runBlocking {
        io.mockk.mockkObject(com.clockweather.app.util.ActiveWidgetDetector)
        io.mockk.every { com.clockweather.app.util.ActiveWidgetDetector.hasActiveWidgets(any(), any()) } returns true
        try {
            BootCompletedReceiver().restoreWeatherUpdates(context)

            val workManager = WorkManager.getInstance(context)
            eventually {
                assertEquals(
                    1,
                    workManager.getWorkInfosForUniqueWork(WeatherUpdateWorker.WORK_NAME).get().size,
                )
                assertEquals(
                    1,
                    workManager.getWorkInfosForUniqueWork(WeatherUpdateScheduler.IMMEDIATE_WORK_NAME).get().size,
                )
            }
        } finally {
            io.mockk.unmockkObject(com.clockweather.app.util.ActiveWidgetDetector)
        }
    }

    @Test
    fun `boot skips scheduling when no active widgets exist`() = runBlocking {
        io.mockk.mockkObject(com.clockweather.app.util.ActiveWidgetDetector)
        io.mockk.every { com.clockweather.app.util.ActiveWidgetDetector.hasActiveWidgets(any(), any()) } returns false
        try {
            BootCompletedReceiver().restoreWeatherUpdates(context)

            val workManager = WorkManager.getInstance(context)
            assertEquals(
                0,
                workManager.getWorkInfosForUniqueWork(WeatherUpdateWorker.WORK_NAME).get().size,
            )
            assertEquals(
                0,
                workManager.getWorkInfosForUniqueWork(WeatherUpdateScheduler.IMMEDIATE_WORK_NAME).get().size,
            )
        } finally {
            io.mockk.unmockkObject(com.clockweather.app.util.ActiveWidgetDetector)
        }
    }

    private fun eventually(assertion: () -> Unit) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        var lastFailure: AssertionError? = null
        while (System.nanoTime() < deadline) {
            try {
                assertion()
                return
            } catch (error: AssertionError) {
                lastFailure = error
                Thread.yield()
            }
        }
        throw lastFailure ?: AssertionError("Condition was not met")
    }
}
