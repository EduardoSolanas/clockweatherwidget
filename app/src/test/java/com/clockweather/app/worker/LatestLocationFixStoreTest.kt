package com.clockweather.app.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression coverage for passive-fix coalescing. The queued WorkManager request is
 * deliberately represented by a real Preferences DataStore: a later fix must survive
 * the KEEP request and be the one a worker reads when it finally starts.
 */
@RunWith(RobolectricTestRunner::class)
class LatestLocationFixStoreTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var file: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = context.preferencesDataStoreFile("latest_fix_${System.nanoTime()}")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun `newer C replaces delayed B and carries its observation age`() = runTest {
        LatestLocationFixStore.record(dataStore, latitude = 51.50, longitude = -0.12, fixTimeMs = 2_000L)
        LatestLocationFixStore.record(dataStore, latitude = 50.82, longitude = -0.14, fixTimeMs = 3_000L)

        val latest = LatestLocationFixStore.read(dataStore.data.first())

        assertTrue(latest != null)
        assertEquals(50.82, latest!!.latitude, 0.000001)
        assertEquals(-0.14, latest.longitude, 0.000001)
        assertEquals(3_000L, latest.fixTimeMs)
    }

    @Test
    fun `older delayed B cannot replace already accepted C`() = runTest {
        LatestLocationFixStore.record(dataStore, latitude = 50.82, longitude = -0.14, fixTimeMs = 3_000L)
        LatestLocationFixStore.record(dataStore, latitude = 51.50, longitude = -0.12, fixTimeMs = 2_000L)

        val latest = LatestLocationFixStore.read(dataStore.data.first())

        assertEquals(50.82, latest!!.latitude, 0.000001)
        assertEquals(3_000L, latest.fixTimeMs)
    }
}
