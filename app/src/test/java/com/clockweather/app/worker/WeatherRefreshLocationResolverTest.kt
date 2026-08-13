package com.clockweather.app.worker

import com.clockweather.app.domain.model.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshLocationResolverTest {

    @Test
    fun `current location uses fresh coordinates while preserving database identity and flag`() {
        val saved = Location(
            id = 42,
            name = "Old place",
            country = "GB",
            latitude = 51.0,
            longitude = -0.1,
            isCurrentLocation = true,
        )
        val detected = Location(
            name = "New place",
            country = "GB",
            latitude = 52.0,
            longitude = -1.0,
            isCurrentLocation = true,
        )

        val refreshed = WeatherRefreshLocationResolver.resolve(saved, detected)

        assertEquals(detected.copy(id = 42, isCurrentLocation = true), refreshed)
    }

    @Test
    fun `fixed saved location remains unchanged`() {
        val saved = Location(
            id = 7,
            name = "Paris",
            country = "FR",
            latitude = 48.8566,
            longitude = 2.3522,
            isCurrentLocation = false,
        )
        val unrelatedDetection = Location(
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true,
        )

        assertEquals(saved, WeatherRefreshLocationResolver.resolve(saved, unrelatedDetection))
    }

    @Test
    fun `current location remains unchanged when no fresh fix is available`() {
        val saved = Location(
            id = 42,
            name = "Old place",
            country = "GB",
            latitude = 51.0,
            longitude = -0.1,
            isCurrentLocation = true,
        )

        assertEquals(saved, WeatherRefreshLocationResolver.resolve(saved, null))
    }

    @Test
    fun `travelling to another city counts as a significant move`() {
        assertTrue(WeatherRefreshLocationResolver.hasMovedSignificantly(LONDON, BRIGHTON))
    }

    @Test
    fun `gps jitter around the same spot is not a significant move`() {
        val jittered = LONDON.copy(latitude = 51.50755, longitude = -0.12805)

        assertFalse(WeatherRefreshLocationResolver.hasMovedSignificantly(LONDON, jittered))
    }

    @Test
    fun `crossing town stays inside the same weather grid`() {
        val acrossTown = LONDON.copy(name = "Shoreditch", latitude = 51.5200, longitude = -0.0900)

        assertFalse(WeatherRefreshLocationResolver.hasMovedSignificantly(LONDON, acrossTown))
    }

    @Test
    fun `moving more than a few kilometres counts as a significant move`() {
        val nextTown = LONDON.copy(name = "Enfield", latitude = 51.5674, longitude = -0.1278)

        assertTrue(WeatherRefreshLocationResolver.hasMovedSignificantly(LONDON, nextTown))
    }

    private companion object {
        val LONDON = Location(
            id = 1,
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true,
        )
        val BRIGHTON = Location(
            id = 1,
            name = "Brighton",
            country = "GB",
            latitude = 50.8225,
            longitude = -0.1372,
            isCurrentLocation = true,
        )
    }
}
