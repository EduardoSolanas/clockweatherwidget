package com.clockweather.app.util

import android.Manifest
import android.app.Application
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PassiveLocationManagerTest {

    private lateinit var context: Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fusedLocationClient = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `pending intent is created with target action`() {
        val pendingIntent = PassiveLocationManager.getPendingIntent(context)
        assertNotNull(pendingIntent)
    }

    @Test
    fun `register skips when location permission is not granted`() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

        PassiveLocationManager.register(context, fusedLocationClient)

        verify(exactly = 0) { fusedLocationClient.requestLocationUpdates(any<LocationRequest>(), any()) }
    }

    @Test
    fun `register submits passive request with 5km threshold when permission is granted`() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )

        val requestSlot = slot<LocationRequest>()
        every {
            fusedLocationClient.requestLocationUpdates(capture(requestSlot), any())
        } returns mockk()

        PassiveLocationManager.register(context, fusedLocationClient)

        verify(atLeast = 1) { fusedLocationClient.requestLocationUpdates(any<LocationRequest>(), any()) }
        assertEquals(Priority.PRIORITY_PASSIVE, requestSlot.captured.priority)
        assertEquals(5000f, requestSlot.captured.minUpdateDistanceMeters, 0.1f)
    }

    @Test
    @Config(sdk = [33])
    fun `register skips when only foreground location is granted`() {
        // Passive delivery to a background receiver still needs the "all the time"
        // grant on Android 10+. Registering without it reports success and then
        // silently never fires — the exact setup the widget got stuck under.
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        PassiveLocationManager.register(context, fusedLocationClient)

        verify(exactly = 0) { fusedLocationClient.requestLocationUpdates(any<LocationRequest>(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `register proceeds on android 9 where foreground location covers background`() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        every { fusedLocationClient.requestLocationUpdates(any<LocationRequest>(), any()) } returns mockk()

        PassiveLocationManager.register(context, fusedLocationClient)

        verify(atLeast = 1) { fusedLocationClient.requestLocationUpdates(any<LocationRequest>(), any()) }
    }

    @Test
    fun `unregister removes location updates`() {
        every { fusedLocationClient.removeLocationUpdates(any<android.app.PendingIntent>()) } returns mockk()

        PassiveLocationManager.unregister(context, fusedLocationClient)

        verify(atLeast = 1) { fusedLocationClient.removeLocationUpdates(any<android.app.PendingIntent>()) }
    }
}
