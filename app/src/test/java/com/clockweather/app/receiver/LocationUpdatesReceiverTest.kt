package com.clockweather.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location as AndroidLocation
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.worker.WeatherUpdateScheduler
import com.google.android.gms.location.LocationResult
import dagger.hilt.android.EntryPointAccessors
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class LocationUpdatesReceiverTest {

    private lateinit var context: Context
    private lateinit var locationRepo: LocationRepository
    private lateinit var entryPoint: WidgetEntryPoint

    private val savedLondon = Location(
        id = 1L,
        name = "London",
        country = "GB",
        latitude = 51.5074,
        longitude = -0.1278,
        isCurrentLocation = true
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        locationRepo = mockk(relaxed = true)
        entryPoint = mockk(relaxed = true)

        every { entryPoint.locationRepository() } returns locationRepo
        every { locationRepo.getSavedLocations() } returns flowOf(listOf(savedLondon))

        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), WidgetEntryPoint::class.java)
        } returns entryPoint

        mockkObject(WeatherUpdateScheduler)
        every { WeatherUpdateScheduler.scheduleUserRefresh(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createLocationIntent(latitude: Double, longitude: Double): Intent {
        val androidLocation = AndroidLocation("fused").apply {
            this.latitude = latitude
            this.longitude = longitude
            time = System.currentTimeMillis()
        }
        val locationResult = LocationResult.create(listOf(androidLocation))
        return Intent(LocationUpdatesReceiver.ACTION_LOCATION_UPDATE).apply {
            putExtra("com.google.android.gms.location.EXTRA_LOCATION_RESULT", locationResult)
        }
    }

    /**
     * Runs the handler on an unconfined dispatcher so it completes before onReceive
     * returns. Sleeping a fixed interval instead would let the negative cases pass
     * merely because the work had not happened *yet*.
     */
    private fun receiveAndSettle(intent: Intent) {
        LocationUpdatesReceiver(Dispatchers.Unconfined).onReceive(context, intent)
    }

    @Test
    fun `significant move enqueues a refresh`() {
        // Brighton (~75 km from London)
        receiveAndSettle(createLocationIntent(50.8225, -0.1372))

        verify(atLeast = 1) { WeatherUpdateScheduler.scheduleUserRefresh(any()) }
    }

    @Test
    fun `significant move does not write a half-updated location row`() {
        // The fix carries coordinates but no city name, and reverse geocoding is not
        // allowed here. Persisting coordinates alone leaves the row naming one city
        // while pointing at another, which survives every worker run whose own fix
        // attempt comes back null. The worker resolves both together or neither.
        receiveAndSettle(createLocationIntent(50.8225, -0.1372))

        coVerify(exactly = 0) { locationRepo.saveLocation(any()) }
    }

    @Test
    fun `minor move under 5km ignores fix and does not trigger refresh`() {
        // Jitter within London (~50 metres)
        receiveAndSettle(createLocationIntent(51.5076, -0.1280))

        coVerify(exactly = 0) { locationRepo.saveLocation(any()) }
        verify(exactly = 0) { WeatherUpdateScheduler.scheduleUserRefresh(any()) }
    }

    @Test
    fun `a stalled repository still releases the broadcast before the ANR budget`() = runTest {
        // goAsync() holds the broadcast open until finish(). Without a bound, a
        // repository read that never returns keeps holding it past the ~10s budget
        // and the platform kills the process.
        every { locationRepo.getSavedLocations() } returns flow { awaitCancellation() }

        val receiver = spyk(LocationUpdatesReceiver(StandardTestDispatcher(testScheduler)))
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult

        receiver.onReceive(context, createLocationIntent(50.8225, -0.1372))
        advanceUntilIdle()

        verify(atLeast = 1) { pendingResult.finish() }
    }

    @Test
    fun `intent without location result is safely ignored`() {
        receiveAndSettle(Intent(LocationUpdatesReceiver.ACTION_LOCATION_UPDATE))

        coVerify(exactly = 0) { locationRepo.saveLocation(any()) }
        verify(exactly = 0) { WeatherUpdateScheduler.scheduleUserRefresh(any()) }
    }
}
