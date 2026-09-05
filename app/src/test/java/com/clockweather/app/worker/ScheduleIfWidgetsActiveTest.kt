package com.clockweather.app.worker

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Finding 7 of docs/TIME_WEATHER_SYNC_REVIEW.md.
 *
 * Boot and screen wake already return early when no widget is placed, but changing the refresh
 * interval in settings scheduled periodic work regardless. Periodic weather work exists to keep
 * widgets current, so a device with none placed should not be running it — the chosen interval
 * is still saved, and placing a widget applies it through onEnabled.
 *
 * No widget is bound in this environment, so the detector reports the real absence rather than
 * a stubbed one.
 */
@RunWith(RobolectricTestRunner::class)
class ScheduleIfWidgetsActiveTest {

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
    fun `changing the interval with no widget placed schedules nothing`() {
        WeatherUpdateScheduler.scheduleIfWidgetsActive(context, intervalMinutes = 60)

        assertEquals(
            0,
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WeatherUpdateWorker.WORK_NAME).get().size,
        )
    }

    /** The unguarded entry point still exists for callers that have already checked. */
    @Test
    fun `the direct scheduler still enqueues periodic work`() {
        WeatherUpdateScheduler.schedule(context, intervalMinutes = 60)

        assertEquals(
            1,
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WeatherUpdateWorker.WORK_NAME).get().size,
        )
    }
}
