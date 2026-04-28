package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WeatherUpdateSchedulerOnCreateTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val testFile = File(context.filesDir, "test_ensure_scheduled.preferences_pb")
        testFile.delete()
        dataStore = PreferenceDataStoreFactory.create { testFile }
        mockkObject(WeatherUpdateScheduler)
        every { WeatherUpdateScheduler.schedule(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(WeatherUpdateScheduler)
    }

    @Test
    fun `ensureScheduled uses stored interval from preferences`() = runTest {
        val key = intPreferencesKey("update_interval_minutes")
        dataStore.edit { it[key] = 45 }

        WeatherUpdateScheduler.ensureScheduled(context, dataStore)

        verify(exactly = 1) { WeatherUpdateScheduler.schedule(context, 45) }
    }

    @Test
    fun `ensureScheduled defaults to 30 minutes when no preference set`() = runTest {
        WeatherUpdateScheduler.ensureScheduled(context, dataStore)

        verify(exactly = 1) { WeatherUpdateScheduler.schedule(context, 30) }
    }
}
