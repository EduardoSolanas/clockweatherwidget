package com.clockweather.app.data.repository

import android.content.Context
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.entity.LocationEntity
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.remote.api.NominatimReverseGeocodingApi
import com.clockweather.app.data.remote.api.OpenMeteoGeocodingApi
import com.clockweather.app.data.remote.dto.GeocodingResponseDto
import com.clockweather.app.data.remote.dto.NominatimReverseGeocodingResponseDto
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A failed fix must not masquerade as a successful one. Handing back the previously
 * saved row makes "we could not locate you" indistinguishable from "you have not
 * moved", which is how the widget sat on a stale city without anything noticing.
 * Every caller already treats null as "no fix" and picks its own fallback.
 */
@RunWith(RobolectricTestRunner::class)
class LocationRepositoryNoFixTest {

    private lateinit var context: Context
    private lateinit var locationDao: FakeLocationDao
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        locationDao = FakeLocationDao(
            LocationEntity(
                id = 1L,
                name = "London",
                country = "GB",
                latitude = 51.5074,
                longitude = -0.1278,
                timezone = "auto",
                isCurrentLocation = true,
            )
        )
        // The fused provider is a Play Services class with no unit-testable
        // constructor; everything else here is the real collaborator.
        fusedLocationClient = mockk()
    }

    private fun repository() = LocationRepositoryImpl(
        context = context,
        locationDao = locationDao,
        geocodingApi = FakeGeocodingApi(),
        reverseGeocodingApi = FakeReverseGeocodingApi(),
        entityMapper = WeatherEntityMapper(),
        dtoMapper = WeatherDtoMapper(),
        fusedLocationClient = fusedLocationClient,
    )

    @Test
    fun `no fix returns null rather than the previously saved location`() = runTest {
        every { fusedLocationClient.lastLocation } returns Tasks.forResult(null)
        every { fusedLocationClient.getCurrentLocation(any<Int>(), any()) } returns Tasks.forResult(null)

        assertNull(repository().getCurrentLocation())
    }

    @Test
    fun `a failing location provider returns null rather than the previously saved location`() = runTest {
        every { fusedLocationClient.lastLocation } returns
            Tasks.forException(IllegalStateException("play services unavailable"))

        assertNull(repository().getCurrentLocation())
    }

    @Test
    fun `lastKnown older than 6 hours returns null when active fix times out`() = runTest {
        val staleLocation = android.location.Location("fused").apply {
            latitude = 50.8225
            longitude = -0.1372
            time = System.currentTimeMillis() - (7 * 60 * 60 * 1000L) // 7 hours old
        }
        every { fusedLocationClient.lastLocation } returns Tasks.forResult(staleLocation)
        every { fusedLocationClient.getCurrentLocation(any<Int>(), any()) } returns Tasks.forResult(null)

        assertNull(repository().getCurrentLocation())
    }

    @Test
    fun `lastKnown within 6 hours is used when active fix times out`() = runTest {
        val recentLocation = android.location.Location("fused").apply {
            latitude = 50.8225
            longitude = -0.1372
            time = System.currentTimeMillis() - (30 * 60 * 1000L) // 30 minutes old
        }
        every { fusedLocationClient.lastLocation } returns Tasks.forResult(recentLocation)
        every { fusedLocationClient.getCurrentLocation(any<Int>(), any()) } returns Tasks.forResult(null)

        val result = repository().getCurrentLocation()
        org.junit.Assert.assertNotNull(result)
        org.junit.Assert.assertEquals(50.8225, result!!.latitude, 0.0001)
        org.junit.Assert.assertEquals(-0.1372, result.longitude, 0.0001)
    }

    private class FakeLocationDao(private val current: LocationEntity?) : LocationDao {
        override fun getAllLocations(): Flow<List<LocationEntity>> = flowOf(listOfNotNull(current))
        override fun getLocationById(id: Long): Flow<LocationEntity?> =
            flowOf(current?.takeIf { it.id == id })

        override suspend fun getCurrentLocation(): LocationEntity? = current
        override suspend fun insertLocation(entity: LocationEntity): Long = entity.id
        override suspend fun updateLocation(entity: LocationEntity) = Unit
        override suspend fun deleteLocation(id: Long) = Unit
        override suspend fun clearCurrentLocation() = Unit
    }

    private class FakeGeocodingApi : OpenMeteoGeocodingApi {
        override suspend fun searchLocations(
            name: String,
            count: Int,
            language: String,
            format: String,
        ): GeocodingResponseDto = GeocodingResponseDto(results = emptyList(), generationtime_ms = null)
    }

    private class FakeReverseGeocodingApi : NominatimReverseGeocodingApi {
        override suspend fun reverseGeocode(
            latitude: Double,
            longitude: Double,
            format: String,
            addressDetails: Int,
            zoom: Int,
        ): NominatimReverseGeocodingResponseDto = throw UnsupportedOperationException(
            "not reached: there is no fix to reverse geocode"
        )
    }
}
